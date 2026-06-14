// 그래프에서 런타임 오류 패턴을 정적 분석으로 감지하는 서비스
package com.codeprint.application.graph;

import com.codeprint.domain.graph.Edge;
import com.codeprint.domain.graph.EdgeType;
import com.codeprint.domain.graph.Node;
import com.codeprint.domain.graph.NodeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class GraphWarningService {

    // 그래프 노드·엣지에서 경고 목록을 생성
    public List<Map<String, Object>> detect(List<Node> nodes, List<Edge> edges) {
        List<Map<String, Object>> warnings = new ArrayList<>();
        warnings.addAll(detectCyclicImports(nodes, edges));
        warnings.addAll(detectBrokenInterfaceChains(nodes, edges));
        warnings.addAll(detectAsyncSelfCalls(nodes, edges));
        warnings.addAll(detectMissingConverterMigration(nodes));
        warnings.addAll(detectDeadCode(nodes, edges));
        warnings.addAll(detectHighFanOut(nodes, edges));
        // DDD 폴더 구조(/domain/, /application/, /infrastructure/)를 사용하는 프로젝트에만 적용
        if (isDddProject(nodes)) {
            warnings.addAll(detectDbLayerBypass(nodes, edges));
            warnings.addAll(detectCrossContextDomainImport(nodes, edges));
            warnings.addAll(detectDomainInfraImport(nodes, edges));
            warnings.addAll(detectCrossDomainFunctionCall(nodes, edges));
        }
        // 각 경고에 안정적 fingerprint 부여 (type+message 기반) — 재분석으로 그래프가 바뀌어도 동일 경고면 동일 값 → suppress 식별용
        for (Map<String, Object> w : warnings) {
            w.put("fingerprint", fingerprint((String) w.get("type"), (String) w.get("message")));
        }
        return warnings;
    }

    // 경고의 안정적 식별자 — SHA-256(type + "|" + message) 16진 문자열. message는 파일명·도메인명 등 안정적 의미 내용에서 파생됨.
    static String fingerprint(String type, String message) {
        try {
            byte[] hash = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(((type == null ? "" : type) + "|" + (message == null ? "" : message))
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    // 노드 파일 경로에서 DDD 레이어(/domain/, /application/, /infrastructure/)가 2개 이상 발견되면 DDD 프로젝트로 판단
    private boolean isDddProject(List<Node> nodes) {
        Set<String> foundLayers = new HashSet<>();
        for (Node n : nodes) {
            String fp = n.getFilePath() != null ? n.getFilePath() : "";
            if (fp.contains("/domain/")) foundLayers.add("domain");
            if (fp.contains("/application/")) foundLayers.add("application");
            if (fp.contains("/infrastructure/")) foundLayers.add("infrastructure");
            if (foundLayers.size() >= 2) return true;
        }
        return false;
    }

    // IMPORT 엣지에서 순환 의존 탐지 (DFS 사이클 검출)
    private List<Map<String, Object>> detectCyclicImports(List<Node> nodes, List<Edge> edges) {
        Map<UUID, Set<UUID>> adj = new HashMap<>();
        Map<UUID, String> nameMap = new HashMap<>();
        // (src, tgt) → edgeId 역인덱스 — 사이클 엣지 ID 수집용
        Map<String, String> importEdgeIds = new HashMap<>();

        for (Node n : nodes) {
            if (n.getType() == NodeType.FILE) {
                adj.put(n.getId(), new HashSet<>());
                nameMap.put(n.getId(), n.getName());
            }
        }
        for (Edge e : edges) {
            if (e.getType() == EdgeType.IMPORT && adj.containsKey(e.getSourceNodeId())) {
                adj.get(e.getSourceNodeId()).add(e.getTargetNodeId());
                importEdgeIds.put(e.getSourceNodeId() + ">" + e.getTargetNodeId(), e.getId().toString());
            }
        }

        Set<UUID> visited = new HashSet<>();
        Set<UUID> stack = new HashSet<>();
        List<List<UUID>> cycles = new ArrayList<>();

        for (UUID start : adj.keySet()) {
            if (!visited.contains(start)) {
                List<UUID> path = new ArrayList<>();
                dfsCycle(start, adj, visited, stack, path, cycles);
            }
        }

        List<Map<String, Object>> warnings = new ArrayList<>();
        for (List<UUID> cycle : cycles) {
            List<String> names = cycle.stream()
                    .map(id -> nameMap.getOrDefault(id, id.toString()))
                    .toList();
            // 사이클을 형성하는 IMPORT 엣지 ID 수집
            List<String> edgeIds = new ArrayList<>();
            int sz = cycle.size();
            for (int i = 0; i < sz; i++) {
                String key = cycle.get(i) + ">" + cycle.get((i + 1) % sz);
                String eid = importEdgeIds.get(key);
                if (eid != null) edgeIds.add(eid);
            }
            Map<String, Object> w = new LinkedHashMap<>();
            w.put("type", "CYCLIC_IMPORT");
            w.put("severity", "HIGH");
            w.put("nodeIds", cycle.stream().map(UUID::toString).toList());
            w.put("edgeIds", edgeIds);
            w.put("message", "순환 의존: " + String.join(" → ", names));
            warnings.add(w);
        }
        return warnings;
    }

    // DFS로 사이클 탐지 — 스택에 있는 노드로 역방향 엣지가 오면 사이클
    private void dfsCycle(UUID node, Map<UUID, Set<UUID>> adj,
                          Set<UUID> visited, Set<UUID> stack,
                          List<UUID> path, List<List<UUID>> cycles) {
        visited.add(node);
        stack.add(node);
        path.add(node);

        for (UUID neighbor : adj.getOrDefault(node, Set.of())) {
            if (!visited.contains(neighbor)) {
                dfsCycle(neighbor, adj, visited, stack, path, cycles);
            } else if (stack.contains(neighbor)) {
                int idx = path.indexOf(neighbor);
                if (idx >= 0) {
                    cycles.add(new ArrayList<>(path.subList(idx, path.size())));
                }
            }
        }

        stack.remove(node);
        path.remove(path.size() - 1);
    }

    // isInterfaceImpl 메타데이터가 있는 FUNCTION_CALL 엣지의 소스가 인터페이스인데 대응 구현체 엣지가 없는 경우
    private List<Map<String, Object>> detectBrokenInterfaceChains(List<Node> nodes, List<Edge> edges) {
        // interfaceImpl 엣지 대상 nodeId 수집
        Set<UUID> implTargets = new HashSet<>();
        for (Edge e : edges) {
            if (e.getType() == EdgeType.FUNCTION_CALL) {
                Object isImpl = e.getMetadata() != null ? e.getMetadata().get("isInterfaceImpl") : null;
                if (Boolean.TRUE.equals(isImpl)) {
                    implTargets.add(e.getTargetNodeId());
                }
            }
        }

        // FUNCTION 노드 중 isInterface=true인데 implTargets에 없는 노드
        Map<UUID, String> nameMap = new HashMap<>();
        for (Node n : nodes) {
            nameMap.put(n.getId(), n.getName());
        }

        List<Map<String, Object>> warnings = new ArrayList<>();
        for (Node n : nodes) {
            if (n.getType() != NodeType.FUNCTION) continue;
            Map<String, Object> meta = n.getMetadata();
            if (meta == null) continue;
            Object isInterface = meta.get("isInterface");
            if (!Boolean.TRUE.equals(isInterface)) continue;

            if (!implTargets.contains(n.getId())) {
                Map<String, Object> w = new LinkedHashMap<>();
                w.put("type", "BROKEN_INTERFACE_CHAIN");
                w.put("severity", "MEDIUM");
                w.put("nodeIds", List.of(n.getId().toString()));
                w.put("edgeIds", List.of());
                w.put("message", "인터페이스 체인 끊김: " + n.getName() + " — 구현체 메서드로 가는 엣지 없음");
                warnings.add(w);
            }
        }
        return warnings;
    }

    // 같은 파일 내 @Async 메서드로 향하는 직접 FUNCTION_CALL — 프록시 우회로 @Async 무시됨
    private List<Map<String, Object>> detectAsyncSelfCalls(List<Node> nodes, List<Edge> edges) {
        // isAsync=true인 FUNCTION 노드 수집 (nodeId → filePath)
        Map<UUID, String> asyncFuncFilePaths = new HashMap<>();
        Map<UUID, String> funcFilePaths = new HashMap<>();
        Map<UUID, String> nameMap = new HashMap<>();

        for (Node n : nodes) {
            if (n.getType() != NodeType.FUNCTION) continue;
            funcFilePaths.put(n.getId(), n.getFilePath() != null ? n.getFilePath() : "");
            nameMap.put(n.getId(), n.getName());
            Map<String, Object> meta = n.getMetadata();
            if (meta != null && Boolean.TRUE.equals(meta.get("isAsync"))) {
                asyncFuncFilePaths.put(n.getId(), n.getFilePath() != null ? n.getFilePath() : "");
            }
        }

        List<Map<String, Object>> warnings = new ArrayList<>();
        for (Edge e : edges) {
            if (e.getType() != EdgeType.FUNCTION_CALL) continue;
            UUID target = e.getTargetNodeId();
            if (!asyncFuncFilePaths.containsKey(target)) continue;

            UUID source = e.getSourceNodeId();
            String sourceFile = funcFilePaths.getOrDefault(source, "");
            String targetFile = asyncFuncFilePaths.get(target);

            // 같은 파일 내 @Async 메서드 호출 — 프록시 우회
            if (!sourceFile.isEmpty() && sourceFile.equals(targetFile)) {
                Map<String, Object> w = new LinkedHashMap<>();
                w.put("type", "ASYNC_SELF_CALL");
                w.put("severity", "MEDIUM");
                w.put("nodeIds", List.of(source.toString(), target.toString()));
                w.put("edgeIds", List.of(e.getId().toString()));
                w.put("message", "@Async 자기 호출: " + nameMap.getOrDefault(source, source.toString())
                        + " → " + nameMap.getOrDefault(target, target.toString())
                        + " (프록시 우회로 비동기 무시됨)");
                warnings.add(w);
            }
        }
        return warnings;
    }

    // interfaces/ 또는 application/ 레이어가 infrastructure/persistence/ 를 직접 IMPORT — DB 레이어 우회
    // FUNCTION_CALL 엣지는 정규식 분석기가 인터페이스 호출을 구현체로 오추적하므로 제외
    private List<Map<String, Object>> detectDbLayerBypass(List<Node> nodes, List<Edge> edges) {
        Map<UUID, String> nodeFilePaths = new HashMap<>();
        Map<UUID, String> nameMap = new HashMap<>();
        for (Node n : nodes) {
            nodeFilePaths.put(n.getId(), n.getFilePath() != null ? n.getFilePath() : "");
            nameMap.put(n.getId(), n.getName());
        }

        List<Map<String, Object>> warnings = new ArrayList<>();
        for (Edge e : edges) {
            // IMPORT 엣지만 검사 — 직접 persistence 클래스를 import하는 경우가 실제 위반
            if (e.getType() != EdgeType.IMPORT) continue;
            String srcPath = nodeFilePaths.getOrDefault(e.getSourceNodeId(), "");
            String tgtPath = nodeFilePaths.getOrDefault(e.getTargetNodeId(), "");

            boolean srcIsUpperLayer = srcPath.contains("/interfaces/") || srcPath.contains("/application/");
            boolean tgtIsPersistence = tgtPath.contains("/infrastructure/persistence/")
                    || tgtPath.contains("/infrastructure/db/");

            if (srcIsUpperLayer && tgtIsPersistence) {
                // isInterfaceImpl 엣지는 정상 패턴이므로 제외
                Map<String, Object> meta = e.getMetadata();
                if (meta != null && Boolean.TRUE.equals(meta.get("isInterfaceImpl"))) continue;

                Map<String, Object> w = new LinkedHashMap<>();
                w.put("type", "DB_LAYER_BYPASS");
                w.put("severity", "HIGH");
                w.put("nodeIds", List.of(e.getSourceNodeId().toString(), e.getTargetNodeId().toString()));
                w.put("edgeIds", List.of(e.getId().toString()));
                w.put("message", "DB 레이어 우회: " + nameMap.getOrDefault(e.getSourceNodeId(), srcPath)
                        + " → " + nameMap.getOrDefault(e.getTargetNodeId(), tgtPath)
                        + " (domain Repository를 거치지 않는 직접 persistence 호출)");
                warnings.add(w);
            }
        }
        return warnings;
    }

    // application/{contextA}/ 파일이 domain/{contextB}/ 를 직접 IMPORT — DDD 컨텍스트 간 직접 참조 위반
    private List<Map<String, Object>> detectCrossContextDomainImport(List<Node> nodes, List<Edge> edges) {
        Map<UUID, String> nodeFilePaths = new HashMap<>();
        Map<UUID, String> nameMap = new HashMap<>();
        for (Node n : nodes) {
            nodeFilePaths.put(n.getId(), n.getFilePath() != null ? n.getFilePath() : "");
            nameMap.put(n.getId(), n.getName());
        }

        List<Map<String, Object>> warnings = new ArrayList<>();
        for (Edge e : edges) {
            if (e.getType() != EdgeType.IMPORT) continue;
            String srcPath = nodeFilePaths.getOrDefault(e.getSourceNodeId(), "");
            String tgtPath = nodeFilePaths.getOrDefault(e.getTargetNodeId(), "");

            String srcContext = extractContextFromApplicationPath(srcPath);
            String tgtContext = extractContextFromDomainPath(tgtPath);

            if (srcContext != null && tgtContext != null && !srcContext.equals(tgtContext)) {
                Map<String, Object> w = new LinkedHashMap<>();
                w.put("type", "CROSS_CONTEXT_IMPORT");
                w.put("severity", "HIGH");
                w.put("nodeIds", List.of(e.getSourceNodeId().toString(), e.getTargetNodeId().toString()));
                w.put("edgeIds", List.of(e.getId().toString()));
                w.put("message", "DDD 컨텍스트 경계 위반: application/" + srcContext + " → domain/" + tgtContext
                        + " 직접 참조 (ID로만 참조해야 함)");
                warnings.add(w);
            }
        }
        return warnings;
    }

    // "/application/{context}/" 경로에서 컨텍스트명 추출 — 없으면 null
    private String extractContextFromApplicationPath(String path) {
        int idx = path.indexOf("/application/");
        if (idx < 0) return null;
        String after = path.substring(idx + "/application/".length());
        int slash = after.indexOf('/');
        return slash > 0 ? after.substring(0, slash) : null;
    }

    // "/domain/{context}/" 경로에서 컨텍스트명 추출 — 없으면 null
    private String extractContextFromDomainPath(String path) {
        int idx = path.indexOf("/domain/");
        if (idx < 0) return null;
        String after = path.substring(idx + "/domain/".length());
        int slash = after.indexOf('/');
        return slash > 0 ? after.substring(0, slash) : null;
    }

    // DDD 팩토리·JPA·React·콜백 패턴은 정적 FUNCTION_CALL 엣지로 추적 불가 → 제외
    private static final Set<String> FRAMEWORK_CALL_NAMES = Set.of(
        // DDD 팩토리 패턴
        "of", "newId", "create", "from", "build",
        // JPA/공통 레포지토리 메서드 (Spring 런타임 프록시로 호출)
        "findById", "findAll", "findAllById", "save", "saveAll", "deleteById",
        "deleteAll", "existsById", "count",
        // JPA AttributeConverter — Hibernate가 영속화/조회 시 리플렉션으로 호출
        "convertToDatabaseColumn", "convertToEntityAttribute",
        // Object 기본 메서드
        "toString", "equals", "hashCode", "compareTo",
        // Java lifecycle
        "main", "run", "init", "destroy", "close",
        // 생성자 alias
        "생성자",
        // Spring Web MVC — HTTP 핸들러, 예외처리
        "handleException", "handleMethodArgumentNotValid", "handleNoHandlerFound",
        // Spring Security
        "configure", "userDetailsService", "passwordEncoder",
        // Spring Boot 진입점
        "getApplicationContext", "contextLoads",
        // Domain entity 상태 변경 — DI로 호출
        "confirm", "touch", "apply", "activate", "deactivate", "enable", "disable", "reset",
        // 팩토리/기본값 메서드
        "defaultStyle", "empty", "none", "zero"
    );

    // JDK 컬렉션·Optional 메서드 — JDK 타입에서 호출되어 bare-name으로 오추적됨.
    // detectCrossDomainFunctionCall 전용 — detectDeadCode가 쓰는 FRAMEWORK_CALL_NAMES와 분리해 dead-code 탐지에는 영향 없음.
    // map/filter/merge 등 도메인 메서드일 수 있는 Stream 변환 동사는 제외(오탐 회피보다 실제 메서드 보존 우선).
    private static final Set<String> JDK_COLLECTION_CALL_NAMES = Set.of(
        "get", "set", "add", "addAll", "put", "putAll", "remove", "removeAll",
        "contains", "containsKey", "clear", "size", "isEmpty", "keySet", "values",
        "entrySet", "stream", "forEach", "orElse", "orElseGet", "orElseThrow",
        "ifPresent", "getOrDefault", "computeIfAbsent", "anyMatch", "allMatch",
        "noneMatch", "findFirst", "toList"
    );

    // getter/setter·Spring 콜백·JPA 파생쿼리·생성자 패턴 — 정적 분석으로 호출 추적 불가
    private static boolean isFrameworkCallPattern(String name) {
        if (name == null || name.isEmpty()) return false;
        // Lombok/JPA getter/setter 패턴 (get/set: charAt(3), is: charAt(2))
        if ((name.startsWith("get") || name.startsWith("set"))
                && name.length() > 3 && Character.isUpperCase(name.charAt(3))) return true;
        if (name.startsWith("is") && name.length() > 2 && Character.isUpperCase(name.charAt(2))) return true;
        // Spring/이벤트 핸들러 패턴
        if (name.startsWith("on") && name.length() > 2 && Character.isUpperCase(name.charAt(2))) return true;
        if (name.startsWith("handle") && name.length() > 6 && Character.isUpperCase(name.charAt(6))) return true;
        // Spring Data JPA 파생 쿼리 — 런타임 프록시가 메서드명으로 쿼리 생성
        if ((name.startsWith("find") || name.startsWith("count")
                || name.startsWith("existsBy") || name.startsWith("delete"))
                && name.length() > 6) return true;
        // Domain entity 뮤테이션 메서드 — DI 인터페이스를 통해 호출, FUNCTION_CALL 엣지 없음
        if ((name.startsWith("save") || name.startsWith("update") || name.startsWith("toggle")
                || name.startsWith("mark") || name.startsWith("upgrade") || name.startsWith("downgrade"))
                && name.length() > 4) return true;
        // Java record/class 생성자 — 함수명이 PascalCase (첫 글자 대문자)
        if (Character.isUpperCase(name.charAt(0))) return true;
        return false;
    }

    // 테스트 코드 여부 — 경로·파일명·함수명 패턴 (JUnit·pytest·jest/vitest·Go test)
    private boolean isTestArtifact(String fp, String name) {
        if (fp.contains("/test/") || fp.contains("\\test\\")
                || fp.contains("/tests/") || fp.contains("\\tests\\")
                || fp.contains("/__tests__/") || fp.contains("\\__tests__\\")) return true;
        if (fp.endsWith("Test.java") || fp.endsWith("Tests.java")
                || fp.endsWith("Test.kt") || fp.endsWith("Tests.kt")
                || fp.endsWith(".test.ts") || fp.endsWith(".test.tsx")
                || fp.endsWith(".test.js") || fp.endsWith(".test.jsx")
                || fp.endsWith(".spec.ts") || fp.endsWith(".spec.tsx")
                || fp.endsWith(".spec.js") || fp.endsWith(".spec.jsx")
                || fp.endsWith("_test.go") || fp.endsWith("_test.py")) return true;
        // pytest 관례 — test_ 로 시작하는 함수
        if (name.startsWith("test_")) return true;
        return false;
    }

    // DEAD_CODE 신뢰도 게이트 — 미호출 함수 비율이 이 값을 넘으면 호출 추출 자체가 불완전하다고 보고 개별 경고를 생략한다.
    // 캘리브레이션(2026-06-15 실측): 정상 Java 레포 codeprint 0.1%·petclinic 1.0%, 호출 추출이 약한 Python requests 22.6%.
    private static final double DEAD_CODE_UNTRUSTWORTHY_RATIO = 0.15;
    // 함수 수가 적으면 비율이 통계적으로 불안정 — 소형 그래프는 게이트 미적용 (기존 미호출 함수 1~2개 케이스 보호)
    private static final int DEAD_CODE_MIN_FUNCTIONS = 30;

    // FUNCTION 노드 중 아무 FUNCTION_CALL 엣지도 받지 않는 함수 — 데드 코드 후보
    // 아래 5가지 패턴은 정적 분석으로 호출 추적이 불가능하여 false positive 발생:
    //   1. JSX 렌더 (<App />) — React.createElement 호출, FUNCTION_CALL 엣지로 연결 안 됨
    //   2. JPA Repository 메서드 — Spring AOP 프록시가 런타임에 호출
    //   3. DDD 팩토리 메서드(of/create) — import 후 다른 파일에서 사용, cross-file 추적 미완성
    //   4. 콜백 참조(addEventListener(handler)) — 값으로 전달, "호출"이 아님
    //   5. React 컴포넌트(대문자 시작 tsx 함수) — export 후 JSX로 사용
    private List<Map<String, Object>> detectDeadCode(List<Node> nodes, List<Edge> edges) {
        Map<UUID, String> idToName = new HashMap<>();
        for (Node n : nodes) idToName.put(n.getId(), n.getName());

        Set<UUID> calledFuncIds = new HashSet<>();
        // FUNCTION_CALL 타깃의 함수명 — 인터페이스→구현체 다형성 디스패치로 인터페이스 선언 노드엔
        // 인바운드 엣지가 없어도, 같은 이름의 호출이 존재하면 사용 중으로 판단하기 위함
        Set<String> calledFuncNames = new HashSet<>();
        for (Edge e : edges) {
            if (e.getType() == EdgeType.FUNCTION_CALL) {
                calledFuncIds.add(e.getTargetNodeId());
                String tn = idToName.get(e.getTargetNodeId());
                if (tn != null) calledFuncNames.add(tn);
            }
        }

        List<Map<String, Object>> warnings = new ArrayList<>();
        for (Node n : nodes) {
            if (n.getType() != NodeType.FUNCTION) continue;
            if (calledFuncIds.contains(n.getId())) continue;

            String fp = n.getFilePath() != null ? n.getFilePath() : "";
            String name = n.getName() != null ? n.getName() : "";

            // Python 던더 메서드(__init__·__iter__ 등) — 런타임이 호출, 이름으로 불리지 않음
            if (name.length() > 4 && name.startsWith("__") && name.endsWith("__")) continue;
            // 테스트 코드 제외 (경로·파일명·함수명 패턴)
            if (isTestArtifact(fp, name)) continue;
            // interfaces/ 레이어 — 컨트롤러, WebSocket 핸들러 등 외부 진입점
            if (fp.contains("/interfaces/")) continue;
            // React 컴포넌트 — .tsx 파일에서 대문자 시작 함수 (JSX로 렌더링되므로 FUNCTION_CALL 엣지 없음)
            if ((fp.endsWith(".tsx") || fp.endsWith(".jsx")) && !name.isEmpty()
                    && Character.isUpperCase(name.charAt(0))) continue;
            // pages/ · components/ · hooks/ · utils/ · lib/ 레이어 — React 모듈 전체가 export 기반
            if (fp.contains("/pages/") || fp.contains("/components/") || fp.contains("/hooks/")
                    || fp.contains("/utils/") || fp.contains("/lib/")) continue;
            // JPA Repository 구현체 · domain 팩토리 메서드 등 프레임워크 호출 패턴
            if (FRAMEWORK_CALL_NAMES.contains(name)) continue;
            // getter/setter/onXxx/handleXxx 네이밍 패턴 — 프레임워크·Lombok 자동 생성
            if (isFrameworkCallPattern(name)) continue;
            // application/ 레이어 — Spring @Service 메서드는 DI를 통해 호출, FUNCTION_CALL 엣지 없음
            if (fp.contains("/application/")) continue;
            // infrastructure/ 레이어 — Spring @Bean, @EventListener, Filter 등 프레임워크 진입점 다수
            if (fp.contains("/infrastructure/")) continue;
            // domain/ Repository·Port 인터페이스 선언 메서드 — 구현체가 인터페이스를 통해 호출(다형성 디스패치).
            // 같은 이름의 FUNCTION_CALL이 존재하면 사용 중으로 간주 (미호출이면 여전히 데드 코드로 감지).
            boolean isDomainInterfaceDecl = fp.contains("/domain/")
                    && (fp.endsWith("Repository.java") || fp.endsWith("Port.java") || fp.contains("/port/"));
            if (isDomainInterfaceDecl && calledFuncNames.contains(name)) continue;

            Map<String, Object> meta = n.getMetadata();
            if (meta != null) {
                // @Async 메서드는 Spring이 직접 호출 — 제외
                if (Boolean.TRUE.equals(meta.get("isAsync"))) continue;
                if (Boolean.TRUE.equals(meta.get("isConstructor"))) continue;
                // @EventListener, @Scheduled, @Bean — Spring이 직접 호출
                if (Boolean.TRUE.equals(meta.get("isEventListener"))) continue;
                if (Boolean.TRUE.equals(meta.get("isScheduled"))) continue;
                if (Boolean.TRUE.equals(meta.get("isBean"))) continue;
                // 프레임워크 어노테이션/데코레이터(@GetMapping·@Bean·@Override·@InitBinder·Python 데코레이터 등) — 런타임이 호출
                if (Boolean.TRUE.equals(meta.get("isFrameworkAnnotated"))) continue;
            }

            Map<String, Object> w = new LinkedHashMap<>();
            w.put("type", "DEAD_CODE");
            w.put("severity", "LOW");
            w.put("nodeIds", List.of(n.getId().toString()));
            w.put("edgeIds", List.of());
            w.put("message", "데드 코드 후보: " + name + " — 이 함수를 호출하는 곳이 없습니다");
            warnings.add(w);
        }

        // 신뢰도 게이트 — 미호출 비율이 임계를 넘으면 호출 추출이 약한 것이므로 개별 경고 대신 단일 안내로 치환
        long totalFunctions = nodes.stream().filter(n -> n.getType() == NodeType.FUNCTION).count();
        if (totalFunctions >= DEAD_CODE_MIN_FUNCTIONS
                && (double) warnings.size() / totalFunctions >= DEAD_CODE_UNTRUSTWORTHY_RATIO) {
            int pct = (int) Math.round((double) warnings.size() / totalFunctions * 100);
            Map<String, Object> gate = new LinkedHashMap<>();
            gate.put("type", "DEAD_CODE");
            gate.put("severity", "LOW");
            gate.put("nodeIds", List.of());
            gate.put("edgeIds", List.of());
            gate.put("message", "미호출 함수 비율 " + pct + "% (" + warnings.size() + "/" + totalFunctions
                    + ") — 호출 추출 신뢰도가 낮아 개별 DEAD_CODE 경고를 생략했습니다. 동적 디스패치가 많은 언어·패턴일 수 있습니다");
            return List.of(gate);
        }
        return warnings;
    }

    // domain/ 파일이 infrastructure/ 를 직접 IMPORT — 의존 방향 위반 (Domain → Infrastructure 금지)
    // shared/ 는 Shared Kernel이므로 허용
    private List<Map<String, Object>> detectDomainInfraImport(List<Node> nodes, List<Edge> edges) {
        Map<UUID, String> nodeFilePaths = new HashMap<>();
        Map<UUID, String> nameMap = new HashMap<>();
        for (Node n : nodes) {
            nodeFilePaths.put(n.getId(), n.getFilePath() != null ? n.getFilePath() : "");
            nameMap.put(n.getId(), n.getName());
        }

        List<Map<String, Object>> warnings = new ArrayList<>();
        for (Edge e : edges) {
            if (e.getType() != EdgeType.IMPORT) continue;
            String srcPath = nodeFilePaths.getOrDefault(e.getSourceNodeId(), "");
            String tgtPath = nodeFilePaths.getOrDefault(e.getTargetNodeId(), "");

            boolean srcIsDomain = srcPath.contains("/domain/");
            boolean tgtIsInfra = tgtPath.contains("/infrastructure/") && !tgtPath.contains("/shared/");

            if (srcIsDomain && tgtIsInfra) {
                String srcContext = extractContextFromDomainPath(srcPath);
                Map<String, Object> w = new LinkedHashMap<>();
                w.put("type", "DOMAIN_IMPORTS_INFRA");
                w.put("severity", "HIGH");
                w.put("nodeIds", List.of(e.getSourceNodeId().toString(), e.getTargetNodeId().toString()));
                w.put("edgeIds", List.of(e.getId().toString()));
                w.put("message", "DDD 의존 방향 위반: domain/"
                        + (srcContext != null ? srcContext : "?") + " → infrastructure/ 직접 import. "
                        + "공통 관심사는 shared/ 로 이동하거나 domain/port/ 인터페이스로 역전하세요.");
                warnings.add(w);
            }
        }
        return warnings;
    }

    // FUNCTION_CALL 엣지가 도메인 경계를 넘을 때 — Cross-Domain 직접 호출 위반
    // 수정 방법: 호출하는 도메인에 port/ 인터페이스 선언 → infrastructure/adapter/ 에서 구현
    private List<Map<String, Object>> detectCrossDomainFunctionCall(List<Node> nodes, List<Edge> edges) {
        Map<UUID, String> nodeFilePaths = new HashMap<>();
        Map<UUID, String> nameMap = new HashMap<>();
        for (Node n : nodes) {
            nodeFilePaths.put(n.getId(), n.getFilePath() != null ? n.getFilePath() : "");
            nameMap.put(n.getId(), n.getName());
        }

        // 함수명 → 등장 도메인 집합 — 동일 이름이 2개 이상 도메인에 있으면 bare-name 해석이 모호 → 오탐 제외용
        Map<String, Set<String>> funcNameToDomains = new HashMap<>();
        for (Node n : nodes) {
            if (n.getType() != NodeType.FUNCTION) continue;
            String domain = extractBoundedContext(n.getFilePath() != null ? n.getFilePath() : "");
            if (domain == null) continue;
            funcNameToDomains.computeIfAbsent(n.getName(), k -> new HashSet<>()).add(domain);
        }

        List<Map<String, Object>> warnings = new ArrayList<>();
        for (Edge e : edges) {
            if (e.getType() != EdgeType.FUNCTION_CALL) continue;
            String srcPath = nodeFilePaths.getOrDefault(e.getSourceNodeId(), "");
            String tgtPath = nodeFilePaths.getOrDefault(e.getTargetNodeId(), "");

            // infrastructure/ 레이어는 여러 도메인을 브릿지하는 역할 — cross-domain 허용
            if (srcPath.contains("/infrastructure/") || tgtPath.contains("/infrastructure/")) continue;
            // 테스트 코드는 아키텍처 위반 대상이 아님
            if (isTestPath(srcPath) || isTestPath(tgtPath)) continue;

            String srcDomain = extractBoundedContext(srcPath);
            String tgtDomain = extractBoundedContext(tgtPath);

            if (srcDomain == null || tgtDomain == null) continue;
            if (srcDomain.equals(tgtDomain)) continue;
            // shared/ 경유는 허용
            if (tgtPath.contains("/shared/")) continue;
            // port/ 어댑터 경유는 허용 (인터페이스 구현체로 향하는 호출)
            if (tgtPath.contains("/port/") || tgtPath.contains("/adapter/")) continue;

            String srcName = nameMap.getOrDefault(e.getSourceNodeId(), srcDomain);
            String tgtName = nameMap.getOrDefault(e.getTargetNodeId(), tgtDomain);

            // 정규식 분석기가 클래스 한정자 없는 호출(get/save/of 등)을 임의 파일로 오추적 — getter/setter·JPA·팩토리·JDK 컬렉션 패턴 제외
            if (FRAMEWORK_CALL_NAMES.contains(tgtName) || JDK_COLLECTION_CALL_NAMES.contains(tgtName)
                    || isFrameworkCallPattern(tgtName)) continue;
            // 동일 함수명이 2개 이상 도메인에 존재하면 bare-name 해석을 신뢰할 수 없어 제외 (오탐 회피 우선).
            // 트레이드오프: 동명 메서드를 쓰는 실제 cross-domain 호출은 놓칠 수 있음 — application→domain 위반은 IMPORT 기반 CROSS_CONTEXT_IMPORT가 보완 검출.
            Set<String> domainsWithName = funcNameToDomains.get(tgtName);
            if (domainsWithName != null && domainsWithName.size() >= 2) continue;
            Map<String, Object> w = new LinkedHashMap<>();
            w.put("type", "CROSS_DOMAIN_CALL");
            w.put("severity", "MEDIUM");
            w.put("nodeIds", List.of(e.getSourceNodeId().toString(), e.getTargetNodeId().toString()));
            w.put("edgeIds", List.of(e.getId().toString()));
            w.put("message", "Cross-Domain 직접 호출: " + srcDomain + "/" + srcName
                    + " → " + tgtDomain + "/" + tgtName
                    + ". 수정: domain/" + srcDomain + "/port/ 인터페이스 선언 후 "
                    + "infrastructure/adapter/ 에서 구현하세요.");
            warnings.add(w);
        }
        return warnings;
    }

    // 테스트 소스 경로 여부 — src/test(JVM), __tests__, *.test.*, *.spec.*(JS/TS) 패턴.
    // "/test/" 단독 매칭은 "test"라는 이름의 비즈니스 도메인(예: 시험·퀴즈)을 오인하므로 "/src/test/"로 한정.
    private static boolean isTestPath(String path) {
        if (path == null) return false;
        return path.contains("/src/test/") || path.contains("\\src\\test\\")
                || path.contains("/__tests__/") || path.contains("\\__tests__\\")
                || path.contains(".test.") || path.contains(".spec.");
    }

    // 파일 경로에서 Bounded Context 이름 추출 (domain/X, application/X, infrastructure/X → X)
    private String extractBoundedContext(String path) {
        for (String layer : List.of("/domain/", "/application/", "/infrastructure/")) {
            int idx = path.indexOf(layer);
            if (idx < 0) continue;
            String after = path.substring(idx + layer.length());
            int slash = after.indexOf('/');
            if (slash > 0) return after.substring(0, slash);
        }
        return null;
    }

    // FUNCTION 노드가 7개 초과 FUNCTION_CALL 아웃바운드를 가질 때 — 과도한 책임 (High Fan-Out)
    private List<Map<String, Object>> detectHighFanOut(List<Node> nodes, List<Edge> edges) {
        final int THRESHOLD = 7;

        Map<UUID, Integer> fanOutMap = new HashMap<>();
        Map<UUID, String> nameMap = new HashMap<>();
        for (Node n : nodes) {
            if (n.getType() == NodeType.FUNCTION) {
                fanOutMap.put(n.getId(), 0);
                nameMap.put(n.getId(), n.getName());
            }
        }
        for (Edge e : edges) {
            if (e.getType() == EdgeType.FUNCTION_CALL && fanOutMap.containsKey(e.getSourceNodeId())) {
                fanOutMap.merge(e.getSourceNodeId(), 1, Integer::sum);
            }
        }

        List<Map<String, Object>> warnings = new ArrayList<>();
        for (Map.Entry<UUID, Integer> entry : fanOutMap.entrySet()) {
            if (entry.getValue() <= THRESHOLD) continue;
            Map<String, Object> w = new LinkedHashMap<>();
            w.put("type", "HIGH_FAN_OUT");
            w.put("severity", "LOW");
            w.put("nodeIds", List.of(entry.getKey().toString()));
            w.put("edgeIds", List.of());
            w.put("message", "과도한 의존: " + nameMap.getOrDefault(entry.getKey(), entry.getKey().toString())
                    + " — " + entry.getValue() + "개 함수를 호출 (단일 책임 원칙 위반 가능성)");
            warnings.add(w);
        }
        return warnings;
    }

    // DB_TABLE 노드 중 @Convert 컨버터가 있는 컬럼이 있을 때 — 기존 데이터 마이그레이션 누락 가능성 경고
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> detectMissingConverterMigration(List<Node> nodes) {
        List<Map<String, Object>> warnings = new ArrayList<>();
        for (Node n : nodes) {
            if (n.getType() != NodeType.DB_TABLE) continue;
            Map<String, Object> meta = n.getMetadata();
            if (meta == null) continue;
            if (!Boolean.TRUE.equals(meta.get("hasConverter"))) continue;

            // 마이그레이션 완료 플래그가 있으면 건너뜀
            if (Boolean.TRUE.equals(meta.get("converterMigrationDone"))) continue;

            // 컨버터 컬럼명이 _encrypted로 끝나면 처음부터 암호화 설계 — 평문 데이터 없음
            List<Map<String, String>> columns = (List<Map<String, String>>) meta.get("columns");
            if (columns != null && columns.stream()
                    .filter(c -> "true".equals(c.get("hasConverter")))
                    .allMatch(c -> {
                        String col = c.get("columnName");
                        return col != null && col.endsWith("_encrypted");
                    })) continue;

            Map<String, Object> w = new LinkedHashMap<>();
            w.put("type", "MISSING_CONVERTER_MIGRATION");
            w.put("severity", "MEDIUM");
            w.put("nodeIds", List.of(n.getId().toString()));
            w.put("edgeIds", List.of());
            w.put("message", "@Convert 컨버터 감지: " + n.getName()
                    + " — 기존 평문 데이터에 대한 Flyway 마이그레이션이 필요합니다");
            warnings.add(w);
        }
        return warnings;
    }
}
