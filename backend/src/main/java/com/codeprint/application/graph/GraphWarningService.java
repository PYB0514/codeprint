// 그래프에서 런타임 오류 패턴을 정적 분석으로 감지하는 서비스
package com.codeprint.application.graph;

import com.codeprint.domain.graph.ArchitectureIntent;
import com.codeprint.domain.graph.Edge;
import com.codeprint.domain.graph.EdgeType;
import com.codeprint.domain.graph.Node;
import com.codeprint.domain.graph.NodeType;
import com.codeprint.shared.gate.GatePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class GraphWarningService {

    // 게이트 테마별 규칙 타입 — detectActiveTheme()과 detect()의 분기가 항상 같은 목록을 참조하도록 단일 소스로 고정
    // DDD_RULE_TYPES는 "바운디드 컨텍스트" 축(다중 컨텍스트 전제)만 남긴다 — "의존 방향" 축(도메인이 인프라를
    // 몰라야 함)은 DDD/헥사고날/클린 아키텍처 등 어떤 테마에서도 보편이라 UNIVERSAL_RULE_TYPES로 승격했다
    // (DOMAIN_IMPORTS_INFRA·INTERFACES_IMPORTS_INFRA, 2026-07-17 — decisions/DECISIONS_ANALYSIS.md 참조).
    private static final List<String> DDD_RULE_TYPES = List.of(
            "DB_LAYER_BYPASS", "CROSS_CONTEXT_IMPORT", "CROSS_DOMAIN_CALL");
    private static final List<String> LAYERED_RULE_TYPES = List.of("LAYERED_REVERSE_DEPENDENCY", "LAYERED_BYPASS");
    private static final List<String> FEATURE_SLICE_RULE_TYPES = List.of("CROSS_FEATURE_IMPORT", "FEATURE_LAYER_VIOLATION");
    private static final List<String> UNIVERSAL_RULE_TYPES = List.of(
            "CYCLIC_IMPORT", "BROKEN_INTERFACE_CHAIN", "ASYNC_SELF_CALL", "MISSING_CONVERTER_MIGRATION",
            "MISSING_TRANSACTIONAL_DELETE", "DEAD_CODE", "HIGH_FAN_OUT",
            "DOMAIN_IMPORTS_INFRA", "INTERFACES_IMPORTS_INFRA");

    // 그래프 노드·엣지에서 경고 목록을 생성
    public List<Map<String, Object>> detect(List<Node> nodes, List<Edge> edges) {
        return detect(nodes, edges, null);
    }

    // 의도 아키텍처(intent)를 함께 받아 INTENT_DRIFT까지 검사 — intent가 null/빈값이면 기존 경고만 생성(하위호환)
    public List<Map<String, Object>> detect(List<Node> nodes, List<Edge> edges, ArchitectureIntent intent) {
        return detect(nodes, edges, intent, GatePolicy.AUTO);
    }

    // gatePolicy — AUTO(자동감지)/DDD/LAYERED 중 사용자가 명시 선언한 방향으로 자동감지를 오버라이드
    public List<Map<String, Object>> detect(List<Node> nodes, List<Edge> edges, ArchitectureIntent intent, GatePolicy gatePolicy) {
        List<Map<String, Object>> warnings = new ArrayList<>();
        warnings.addAll(detectCyclicImports(nodes, edges));
        warnings.addAll(detectBrokenInterfaceChains(nodes, edges));
        warnings.addAll(detectAsyncSelfCalls(nodes, edges));
        warnings.addAll(detectMissingConverterMigration(nodes));
        warnings.addAll(detectMissingTransactionalDelete(nodes, edges));
        warnings.addAll(detectDeadCode(nodes, edges));
        warnings.addAll(detectHighFanOut(nodes, edges));
        // 의존 방향(도메인→인프라) 위반은 테마 선택과 무관한 보편 원칙이라 정책 분기 밖에서 항상 실행
        warnings.addAll(detectDomainInfraImport(nodes, edges));
        warnings.addAll(detectInterfaceInfraImport(nodes, edges));
        // DDD 폴더 구조(/domain/, /application/, /infrastructure/)를 사용하는 프로젝트에만 적용 — 또는 정책으로 DDD 강제
        boolean useDdd = gatePolicy == GatePolicy.DDD
                || (gatePolicy == GatePolicy.AUTO && isDddProject(nodes));
        if (useDdd) {
            warnings.addAll(detectDbLayerBypass(nodes, edges));
            warnings.addAll(detectCrossContextDomainImport(nodes, edges));
            warnings.addAll(detectCrossDomainFunctionCall(nodes, edges));
        } else {
            // 비DDD(AUTO 미감지 또는 LAYERED 강제) — Controller/Service/Repository 레이어 컨벤션 위반 검사
            // (레이어 2종 미만이면 detectLayeredViolations 자체 게이트로 빈 목록 — LAYERED 강제 시에도 안전)
            warnings.addAll(detectLayeredViolations(nodes, edges));
        }
        // React/JS 피처-슬라이스(features/{X}/) 경계 위반 — 자체 게이트(피처 2개↑ + 프론트 언어)로 해당 레포만 발화
        warnings.addAll(detectCrossFeatureImport(nodes, edges));
        // React/JS 레이어 단방향 위반(app→features→shared) — 하위 레이어가 상위를 import. CROSS_FEATURE와 동일 게이트.
        warnings.addAll(detectFeatureLayerViolation(nodes, edges));
        // 사용자가 선언한 의도 아키텍처와 실제 의존을 대조 (컨벤션 무관 — 비-DDD 프로젝트도 적용)
        warnings.addAll(detectIntentDrift(nodes, edges, intent));
        // 노드 위치 조회용 인덱스 — 경고에 발생 파일 경로를 부여하기 위함
        Map<UUID, String> idToFilePath = new HashMap<>();
        // 노드 위치 조회용 인덱스 — 경고에 발생 줄 번호를 부여(Java/TS/JS FUNCTION 노드만 보유, VS Code 인라인 경고용)
        Map<UUID, Integer> idToLine = new HashMap<>();
        // 노드 위치 조회용 인덱스 — 경고에 발생 컬럼 범위[시작,끝]를 부여(Java/TS/JS FUNCTION 노드만 보유, VS Code 인라인 경고 밑줄 정밀도용)
        Map<UUID, int[]> idToColRange = new HashMap<>();
        for (Node n : nodes) {
            idToFilePath.put(n.getId(), n.getFilePath() != null ? n.getFilePath() : "");
            if (n.getMetadata() != null && n.getMetadata().get("line") instanceof Number num) {
                idToLine.put(n.getId(), num.intValue());
            }
            if (n.getMetadata() != null && n.getMetadata().get("col") instanceof Number colNum
                    && n.getMetadata().get("endCol") instanceof Number endColNum) {
                idToColRange.put(n.getId(), new int[]{colNum.intValue(), endColNum.intValue()});
            }
        }
        // 사용자가 선언한 ignore 패턴(글로브)에 매치되는 경고를 그룹 억제 — opt-out 모델.
        // isEmpty()(모듈/규칙 유무)와 독립적으로 적용한다 — ignores만 선언한 의도도 유효하다.
        if (intent != null) {
            warnings.removeIf(w -> {
                Object raw = w.get("nodeIds");
                List<?> ids = raw instanceof List<?> l ? l : List.of();
                String src = ids.isEmpty() ? "" : fileOfNodeId(ids.get(0), idToFilePath);
                String tgt = ids.size() < 2 ? "" : fileOfNodeId(ids.get(1), idToFilePath);
                return intent.isIgnored((String) w.get("type"), src, tgt);
            });
        }
        for (Map<String, Object> w : warnings) {
            // 각 경고에 안정적 fingerprint 부여 (type+message 기반) — 재분석으로 그래프가 바뀌어도 동일 경고면 동일 값 → suppress 식별용
            w.put("fingerprint", fingerprint((String) w.get("type"), (String) w.get("message")));
            // 경고 발생 위치 파일 — primary 노드(첫 nodeId)의 경로. 그래프 없는 PR 코멘트에서 위치 표시용
            attachPrimaryFile(w, idToFilePath);
            // 경고 발생 줄 번호 — primary 노드가 줄 정보를 보유한 FUNCTION 노드일 때만 부여
            attachPrimaryLine(w, idToLine);
            // 경고 발생 컬럼 범위 — primary 노드가 컬럼 정보를 보유한 FUNCTION 노드일 때만 부여
            attachPrimaryColumn(w, idToColRange);
        }
        // 타입→파일→메시지 안정 정렬 — parallelStream 파싱(#363) 이후 실행마다 경고 집계 순회 순서가 달라져
        // PR 코멘트 diff 노이즈·UI 목록이 재분석마다 튀던 문제 해소(내용은 이미 결정적, 순서만 비결정적이었음)
        warnings.sort(Comparator
                .comparing((Map<String, Object> w) -> String.valueOf(w.get("type")))
                .thenComparing(w -> String.valueOf(w.getOrDefault("file", "")))
                .thenComparing(w -> String.valueOf(w.get("message"))));
        return warnings;
    }

    // 경고의 nodeId(문자열)를 파일 경로로 해소 — UUID 형식 아니거나 미발견이면 빈 문자열
    private String fileOfNodeId(Object rawId, Map<UUID, String> idToFilePath) {
        if (rawId == null) return "";
        try {
            return idToFilePath.getOrDefault(UUID.fromString(String.valueOf(rawId)), "");
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    // 경고의 primary 노드(nodeIds[0]) 정의 줄을 line 필드로 부여 — 줄 정보 없는 노드(파일/DB/API, 또는 Java·TS/JS 외 언어)면 미부여
    private void attachPrimaryLine(Map<String, Object> w, Map<UUID, Integer> idToLine) {
        Object raw = w.get("nodeIds");
        if (!(raw instanceof List<?> nodeIds) || nodeIds.isEmpty()) return;
        try {
            Integer line = idToLine.get(UUID.fromString(String.valueOf(nodeIds.get(0))));
            if (line != null) w.put("line", line);
        } catch (IllegalArgumentException ignored) {
            // nodeIds[0]가 UUID 형식이 아니면 위치 미부여 (방어적)
        }
    }

    // 경고의 primary 노드(nodeIds[0]) 식별자 컬럼 범위를 col/endCol 필드로 부여 — 컬럼 정보 없는 노드면 미부여
    private void attachPrimaryColumn(Map<String, Object> w, Map<UUID, int[]> idToColRange) {
        Object raw = w.get("nodeIds");
        if (!(raw instanceof List<?> nodeIds) || nodeIds.isEmpty()) return;
        try {
            int[] range = idToColRange.get(UUID.fromString(String.valueOf(nodeIds.get(0))));
            if (range != null) {
                w.put("col", range[0]);
                w.put("endCol", range[1]);
            }
        } catch (IllegalArgumentException ignored) {
            // nodeIds[0]가 UUID 형식이 아니면 위치 미부여 (방어적)
        }
    }

    // 경고의 primary 노드(nodeIds[0]) 파일 경로를 file 필드로 부여 — nodeIds가 비었거나 노드 미발견이면 미부여
    private void attachPrimaryFile(Map<String, Object> w, Map<UUID, String> idToFilePath) {
        Object raw = w.get("nodeIds");
        if (!(raw instanceof List<?> nodeIds) || nodeIds.isEmpty()) return;
        try {
            String fp = idToFilePath.get(UUID.fromString(String.valueOf(nodeIds.get(0))));
            if (fp != null && !fp.isEmpty()) w.put("file", fp);
        } catch (IllegalArgumentException ignored) {
            // nodeIds[0]가 UUID 형식이 아니면 위치 미부여 (방어적)
        }
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
            // 레이어 KIND를 디렉터리 별칭으로 인식 — 리터럴 domain/application/infrastructure 외에 core·persistence·adapter 등
            // 실제 명명을 포착해 게이트가 Codeprint 자기 컨벤션에만 묶이지 않게 한다(recall).
            if (containsLayerSegment(fp, DOMAIN_LAYER_DIRS)) foundLayers.add("domain");
            if (containsLayerSegment(fp, APPLICATION_LAYER_DIRS)) foundLayers.add("application");
            if (containsLayerSegment(fp, INFRA_LAYER_DIRS)) foundLayers.add("infrastructure");
            if (foundLayers.size() >= 2) return true;
        }
        return false;
    }

    // 프론트 언어 + 서로 다른 features/{X}/ 디렉터리 2개 이상이면 피처-슬라이스 프로젝트로 판단(CROSS_FEATURE·FEATURE_LAYER 공용 게이트)
    private boolean isFeatureSliceProject(List<Node> nodes) {
        Set<String> features = new HashSet<>();
        boolean hasFrontend = false;
        for (Node n : nodes) {
            String f = featureOf(n.getFilePath());
            if (f != null) features.add(f);
            if (isFrontendLanguage(n.getLanguage())) hasFrontend = true;
        }
        return features.size() >= 2 && hasFrontend;
    }

    // 게이트 테마 표면화용 값 객체 — 현재 적용 중인 규칙 그룹과 각 그룹의 규칙 타입 목록(1~2단계, PROGRESS.md "게이트 테마" 참조)
    // selfDeclared — gatePolicy가 AUTO가 아니라 사용자가 직접 방향을 선언했는지(프론트 "직접 선언" 표시용)
    public record ActiveTheme(String theme, List<String> themeRuleTypes, boolean featureSliceActive,
                               List<String> featureSliceRuleTypes, List<String> universalRuleTypes,
                               boolean dddDetected, GatePolicy gatePolicy, boolean selfDeclared) {}

    // 현재 프로젝트에 적용 중인 게이트 테마(DDD/LAYERED/GENERIC) + 규칙 목록을 계산 — detect()의 분기와 동일 조건 재사용
    public ActiveTheme detectActiveTheme(List<Node> nodes, List<Edge> edges, GatePolicy gatePolicy) {
        boolean dddDetected = isDddProject(nodes);
        boolean featureSliceActive = isFeatureSliceProject(nodes);
        boolean selfDeclared = gatePolicy != GatePolicy.AUTO;
        boolean useDdd = gatePolicy == GatePolicy.DDD || (gatePolicy == GatePolicy.AUTO && dddDetected);
        if (useDdd) {
            return new ActiveTheme("DDD", DDD_RULE_TYPES, featureSliceActive, FEATURE_SLICE_RULE_TYPES,
                    UNIVERSAL_RULE_TYPES, dddDetected, gatePolicy, selfDeclared);
        }
        // LAYERED 강제 시엔 실제 레이어 구조 무관하게 표시(강제 자체가 목적) — AUTO면 자체 게이트(레이어 2종 이상) 충족해야 표시
        boolean useLayered = gatePolicy == GatePolicy.LAYERED || (gatePolicy == GatePolicy.AUTO && hasLayeredStructure(nodes));
        if (useLayered) {
            return new ActiveTheme("LAYERED", LAYERED_RULE_TYPES, featureSliceActive, FEATURE_SLICE_RULE_TYPES,
                    UNIVERSAL_RULE_TYPES, dddDetected, gatePolicy, selfDeclared);
        }
        return new ActiveTheme("GENERIC", List.of(), featureSliceActive, FEATURE_SLICE_RULE_TYPES,
                UNIVERSAL_RULE_TYPES, dddDetected, gatePolicy, selfDeclared);
    }

    // detectLayeredViolations의 "분류된 레이어 2종 이상" 게이트만 떼어내 재사용 — 실제 위반 목록 계산 없이 적용 여부만 판정
    private boolean hasLayeredStructure(List<Node> nodes) {
        if (isFeatureSliceProject(nodes)) return false;
        EnumSet<Layer> present = EnumSet.noneOf(Layer.class);
        for (Node n : nodes) {
            if (n.getType() != NodeType.FILE) continue;
            String fp = n.getFilePath() != null ? n.getFilePath() : "";
            if (isTestPath(fp) || isTestArtifact(fp, n.getName() != null ? n.getName() : "")) continue;
            Layer layer = classifyLayer(fp);
            if (layer != null) present.add(layer);
        }
        return present.size() >= 2;
    }

    // IMPORT 엣지에서 순환 의존 탐지 (DFS 사이클 검출)
    private List<Map<String, Object>> detectCyclicImports(List<Node> nodes, List<Edge> edges) {
        Map<UUID, Set<UUID>> adj = new HashMap<>();
        Map<UUID, String> nameMap = new HashMap<>();
        // 결정론용 안정 정렬 키 — 노드 ID는 실행마다 랜덤 UUID라 HashMap/HashSet 순회 순서가 변해
        // DFS 시작·이웃 방문 순서가 바뀌고 검출되는 사이클 수가 흔들렸다(같은 코드에 CYCLIC 1↔3). 파일 경로는 런 간 불변.
        Map<UUID, String> orderKey = new HashMap<>();
        // (src, tgt) → edgeId 역인덱스 — 사이클 엣지 ID 수집용
        Map<String, String> importEdgeIds = new HashMap<>();

        for (Node n : nodes) {
            // 테스트 코드(벤치 픽스처 포함)의 의도적 순환은 아키텍처 위반이 아니므로 제외 — 다른 탐지기와 동일한 기준.
            if (n.getType() == NodeType.FILE && !isTestPath(n.getFilePath())) {
                adj.put(n.getId(), new HashSet<>());
                nameMap.put(n.getId(), n.getName());
                String fp = n.getFilePath();
                orderKey.put(n.getId(), fp != null && !fp.isEmpty() ? fp
                        : (n.getName() != null ? n.getName() : n.getId().toString()));
            }
        }
        for (Edge e : edges) {
            if (e.getType() == EdgeType.IMPORT && adj.containsKey(e.getSourceNodeId())) {
                adj.get(e.getSourceNodeId()).add(e.getTargetNodeId());
                importEdgeIds.put(e.getSourceNodeId() + ">" + e.getTargetNodeId(), e.getId().toString());
            }
        }

        // 파일 경로 기준 안정 정렬(동률 시 UUID) — 결정론 보장.
        Comparator<UUID> byPath = Comparator
                .comparing((UUID id) -> orderKey.getOrDefault(id, id.toString()))
                .thenComparing(UUID::toString);

        Set<UUID> visited = new HashSet<>();
        Set<UUID> stack = new HashSet<>();
        List<List<UUID>> cycles = new ArrayList<>();

        List<UUID> starts = new ArrayList<>(adj.keySet());
        starts.sort(byPath);
        for (UUID start : starts) {
            if (!visited.contains(start)) {
                List<UUID> path = new ArrayList<>();
                dfsCycle(start, adj, visited, stack, path, cycles, byPath);
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
            w.put("message", "순환 의존: " + String.join(" → ", names)
                    + ". 수정: 공유 로직을 shared/ 모듈로 분리하거나 한쪽 의존을 이벤트/포트-어댑터 패턴으로 역전하세요.");
            warnings.add(w);
        }
        return warnings;
    }

    // DFS로 사이클 탐지 — 스택에 있는 노드로 역방향 엣지가 오면 사이클.
    // 이웃을 order(파일 경로)로 정렬해 방문해 결정론 보장(랜덤 UUID HashSet 순회 순서 의존 제거).
    private void dfsCycle(UUID node, Map<UUID, Set<UUID>> adj,
                          Set<UUID> visited, Set<UUID> stack,
                          List<UUID> path, List<List<UUID>> cycles, Comparator<UUID> order) {
        visited.add(node);
        stack.add(node);
        path.add(node);

        List<UUID> neighbors = new ArrayList<>(adj.getOrDefault(node, Set.of()));
        neighbors.sort(order);
        for (UUID neighbor : neighbors) {
            if (!visited.contains(neighbor)) {
                dfsCycle(neighbor, adj, visited, stack, path, cycles, order);
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
    // ★버그 수정(2026-07-14, 벤치 실측): GraphBuilder는 isInterfaceImpl 엣지를 인터페이스→구현체 방향(source=인터페이스,
    // target=구현체)으로 생성하는데(GraphBuilder.java 인터페이스 매칭 블록), 기존 코드는 getTargetNodeId()를 모아
    // 인터페이스 자신의 nodeId와 대조했다 — target은 항상 구현체 쪽 ID라 인터페이스 ID와 절대 일치할 수 없어,
    // 구현체가 있어도 매번 오탐하는 상태였다(핸드빌트 단위테스트만으로는 엣지 방향이 검증되지 않아 미발견).
    // Spring Data CrudRepository/JpaRepository가 프레임워크 차원에서 제공하는 기본 메서드명 — 도메인 포트
    // 인터페이스가 같은 이름으로 선언해도, 하위 Spring Data 인터페이스 소스에는 이 메서드들이 텍스트로
    // 재선언되지 않는다(상속으로 자동 제공돼 SimpleJpaRepository가 구현) — 정적 분석으로는 구현 엣지를
    // 원천적으로 찾을 수 없어 이름으로 제외한다(도그푸딩 실측 2026-07-14).
    private static final Set<String> SPRING_DATA_BASE_METHODS = Set.of(
            "save", "saveAll", "saveAndFlush", "findById", "findAll", "findAllById",
            "deleteById", "delete", "deleteAll", "deleteAllById", "existsById", "count", "flush");

    private List<Map<String, Object>> detectBrokenInterfaceChains(List<Node> nodes, List<Edge> edges) {
        // isInterfaceImpl 엣지의 소스(인터페이스 쪽) nodeId 수집 — 이 집합에 있으면 구현체 엣지가 존재
        Set<UUID> interfacesWithImpl = new HashSet<>();
        for (Edge e : edges) {
            if (e.getType() == EdgeType.FUNCTION_CALL) {
                Object isImpl = e.getMetadata() != null ? e.getMetadata().get("isInterfaceImpl") : null;
                if (Boolean.TRUE.equals(isImpl)) {
                    interfacesWithImpl.add(e.getSourceNodeId());
                }
            }
        }

        // FUNCTION 노드 중 isInterface=true인데 interfacesWithImpl에 없는 노드
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
            if (SPRING_DATA_BASE_METHODS.contains(n.getName())) continue;

            if (!interfacesWithImpl.contains(n.getId())) {
                Map<String, Object> w = new LinkedHashMap<>();
                w.put("type", "BROKEN_INTERFACE_CHAIN");
                w.put("severity", "MEDIUM");
                w.put("nodeIds", List.of(n.getId().toString()));
                w.put("edgeIds", List.of());
                w.put("message", "인터페이스 체인 끊김: " + n.getName()
                        + " — 구현체 메서드로 가는 엣지가 없습니다. 수정: 인터페이스를 구현하는 클래스가 해당 메서드를 @Override로 선언했는지 확인하세요.");
                warnings.add(w);
            }
        }
        return warnings;
    }

    // @Async 프록시 우회가 실재하는 언어인지 — Spring AOP 프록시 기반 JVM 언어(Java/Kotlin)만 해당
    private boolean isProxyAsyncLanguage(String language) {
        return "java".equalsIgnoreCase(language) || "kotlin".equalsIgnoreCase(language);
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
            // Spring @Async 프록시 우회는 JVM 프록시 기반 언어에만 해당 — JS/TS/Python의 async는 프록시가 없어 같은 파일 호출이 정상
            if (meta != null && Boolean.TRUE.equals(meta.get("isAsync")) && isProxyAsyncLanguage(n.getLanguage())) {
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
                // ★0단계(correctness) 승격(2026-07-14) — @Async가 조용히 무시되는 건 설계 취향이 아니라
                // 개발자가 의도한 비동기 동작 자체가 실행되지 않는 것(작동 실패) — 항상 게이팅 대상.
                w.put("severity", "HIGH");
                w.put("nodeIds", List.of(source.toString(), target.toString()));
                w.put("edgeIds", List.of(e.getId().toString()));
                w.put("message", "@Async 자기 호출: " + nameMap.getOrDefault(source, source.toString())
                        + " → " + nameMap.getOrDefault(target, target.toString())
                        + " (프록시 우회로 비동기 무시됨). 수정: @Async 메서드를 별도 @Service 빈으로 분리하거나"
                        + " ApplicationContext.getBean()으로 프록시를 경유해 호출하세요.");
                warnings.add(w);
            }
        }
        return warnings;
    }

    // Spring Data 파생 삭제 쿼리(deleteBy*/removeBy*) 메서드명 패턴 — JPA 관례
    private static final java.util.regex.Pattern DERIVED_DELETE_METHOD =
            java.util.regex.Pattern.compile("^(delete|remove)By[A-Z]");

    // JpaRepository 파생 삭제 쿼리에 @Transactional 누락 — 같은 원인 클래스가 3번 반복된 뒤([반복] BE-15,
    // decisions/DECISIONS_BACKEND.md) 발견 즉시 InvalidDataAccessApiUsageException(500)으로 이어짐.
    // Java/Kotlin 전용(Spring 개념) + infra∩persistence 레이어로 한정해 무관한 deleteBy* 오탐 배제.
    // ★HIGH 승격(2026-07-14) — 벤치 무오탐(bench/rules/MISSING_TRANSACTIONAL_DELETE) + 자기 레포 0건 확인,
    // 다른 MEDIUM 룰(설계 위생)과 달리 방치하면 실제 런타임 크래시(500)로 이어지는 진짜 버그라 머지 차단 대상으로 승격.
    //
    // ★도그푸딩 실측(2026-07-12)에서 15건 오탐 발견 후 2단계 정밀도 가드 추가:
    // ①deleteById/removeById는 CrudRepository 기본 메서드라 SimpleJpaRepository가 프레임워크 차원에서
    //   이미 @Transactional 처리 — 사용자 정의 파생 쿼리(예: deleteByUserId)와 이름 패턴만으론 구분 불가해 제외.
    // ②"Impl 클래스가 @Transactional로 감싸고 내부 JpaRepository 인터페이스 메서드를 호출"하는 표준 패턴에서,
    //   내부 인터페이스 메서드 자체엔 애노테이션이 없어도 호출자(Impl)가 이미 트랜잭션 경계를 제공 — FUNCTION_CALL
    //   엣지로 "isTransactional=true인 소스에서 오는 호출이 있는지" 확인해 이런 경우는 발화하지 않는다.
    private List<Map<String, Object>> detectMissingTransactionalDelete(List<Node> nodes, List<Edge> edges) {
        Map<UUID, Boolean> nodeIsTransactional = new HashMap<>();
        for (Node n : nodes) {
            if (n.getType() != NodeType.FUNCTION) continue;
            Map<String, Object> meta = n.getMetadata();
            nodeIsTransactional.put(n.getId(), meta != null && Boolean.TRUE.equals(meta.get("isTransactional")));
        }
        // 이 메서드로 향하는 호출 중 이미 트랜잭션 경계를 가진 소스(Impl 래퍼)가 있는 target 집합
        Set<UUID> coveredByTransactionalCaller = new HashSet<>();
        for (Edge e : edges) {
            if (e.getType() != EdgeType.FUNCTION_CALL) continue;
            if (Boolean.TRUE.equals(nodeIsTransactional.get(e.getSourceNodeId()))) {
                coveredByTransactionalCaller.add(e.getTargetNodeId());
            }
        }

        List<Map<String, Object>> warnings = new ArrayList<>();
        for (Node n : nodes) {
            if (n.getType() != NodeType.FUNCTION) continue;
            if (!"Java".equalsIgnoreCase(n.getLanguage()) && !"Kotlin".equalsIgnoreCase(n.getLanguage())) continue;
            String name = n.getName();
            if (name == null || !DERIVED_DELETE_METHOD.matcher(name).find()) continue;
            if (name.equals("deleteById") || name.equals("removeById")) continue;

            String fp = n.getFilePath() != null ? n.getFilePath() : "";
            if (isTestArtifact(fp, name)) continue;
            if (!containsLayerSegment(fp, INFRA_LAYER_DIRS) || !containsLayerSegment(fp, PERSISTENCE_LAYER_DIRS)) continue;

            if (Boolean.TRUE.equals(nodeIsTransactional.get(n.getId()))) continue;
            if (coveredByTransactionalCaller.contains(n.getId())) continue;

            Map<String, Object> w = new LinkedHashMap<>();
            w.put("type", "MISSING_TRANSACTIONAL_DELETE");
            w.put("severity", "HIGH");
            w.put("nodeIds", List.of(n.getId().toString()));
            w.put("edgeIds", List.of());
            w.put("message", "@Transactional 누락: " + name
                    + " — Spring Data 파생 삭제 쿼리에 트랜잭션 경계가 없으면 EntityManager 부재로 런타임 예외가 발생합니다."
                    + " 수정: 메서드에 @Transactional을 추가하세요.");
            warnings.add(w);
        }
        return warnings;
    }

    // 상위 레이어(interfaces/application 별칭)가 영속화 계층(INFRA ∩ persistence 별칭)을 직접 IMPORT — DB 레이어 우회
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

            // 테스트 코드가 레포지토리/영속화를 직접 import하는 것은 정상(통합 테스트 와이어링)이라 위반 아님 — 제외
            if (isTestArtifact(srcPath, nameMap.getOrDefault(e.getSourceNodeId(), ""))) continue;

            // 컴포지션 루트/부트스트랩 클래스는 애플리케이션 시작 시 구체 구현체를 배선하는 것이 설계 의도라
            // 레이어링 규칙의 의도적 예외(IDDD_Samples ApplicationServiceLifeCycle 측정으로 발견, 2026-07-01).
            boolean srcIsUpperLayer = containsLayerSegment(srcPath, UPPER_LAYER_DIRS) && !isCompositionRoot(srcPath);
            // 영속화 타깃 = INFRA 레이어이면서 동시에 영속화 세그먼트를 가진 경로 — 두 조건 교집합으로 한정해
            // infrastructure/service(비영속화)·domain/port/repository(INFRA 밖 도메인 인터페이스) 오탐을 배제한다.
            // errors/exceptions 파일은 리포지토리 CRUD가 아닌 예외 타입 정의라 "직접 persistence 호출"이 아님(py-realworld
            // app/db/errors.py 측정으로 발견 — 라우트가 except EntityDoesNotExist로 잡는 표준 패턴을 오탐으로 지목).
            boolean tgtIsPersistence = containsLayerSegment(tgtPath, INFRA_LAYER_DIRS)
                    && containsLayerSegment(tgtPath, PERSISTENCE_LAYER_DIRS)
                    && !isErrorModule(tgtPath);

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
                        + " (domain Repository를 거치지 않는 직접 persistence 호출). 수정: domain/port/ 에"
                        + " Repository 인터페이스를 선언하고 infrastructure/persistence/ 에서 구현하세요.");
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

        // context-first 레이아웃({context}/application/·{context}/model/) 컨텍스트 집합을 전역 추론.
        // 비어 있으면 layer-first(application/{context}/) 레포 → 기존 추출만 사용(무회귀).
        Set<String> cfContexts = detectContextFirstContexts(nodeFilePaths.values());

        // C1: 진짜 복수 바운디드 컨텍스트가 있을 때만 발화 — 단일 컨텍스트(헥사고날 단일 모듈 등)면 cross-context
        // 위반 자체가 성립 불가. 추출 가능한 distinct 컨텍스트가 2개 미만이면 검출하지 않는다(교과서 FP 방지).
        Set<String> distinctContexts = new HashSet<>();
        for (String p : nodeFilePaths.values()) {
            String ac = applicationContextOf(p, cfContexts);
            if (ac != null) distinctContexts.add(ac);
            String dc = domainContextOf(p, cfContexts);
            if (dc != null) distinctContexts.add(dc);
        }
        if (distinctContexts.size() < 2) return List.of();

        List<Map<String, Object>> warnings = new ArrayList<>();
        for (Edge e : edges) {
            if (e.getType() != EdgeType.IMPORT) continue;
            String srcPath = nodeFilePaths.getOrDefault(e.getSourceNodeId(), "");
            String tgtPath = nodeFilePaths.getOrDefault(e.getTargetNodeId(), "");

            // 테스트 코드의 타 컨텍스트 import는 정상(테스트 픽스처 와이어링)이라 위반 아님 — 제외
            if (isTestArtifact(srcPath, nameMap.getOrDefault(e.getSourceNodeId(), ""))) continue;

            String srcContext = applicationContextOf(srcPath, cfContexts);
            String tgtContext = domainContextOf(tgtPath, cfContexts);

            if (srcContext != null && tgtContext != null && !srcContext.equals(tgtContext)) {
                Map<String, Object> w = new LinkedHashMap<>();
                w.put("type", "CROSS_CONTEXT_IMPORT");
                w.put("severity", "HIGH");
                w.put("nodeIds", List.of(e.getSourceNodeId().toString(), e.getTargetNodeId().toString()));
                w.put("edgeIds", List.of(e.getId().toString()));
                w.put("message", "DDD 컨텍스트 경계 위반: application/" + srcContext + " → domain/" + tgtContext
                        + " 직접 참조. 수정: 타 컨텍스트의 ID(UUID)만 보유하고,"
                        + " 필요 데이터는 domain/port/ 인터페이스로 조회하세요.");
                warnings.add(w);
            }
        }
        return warnings;
    }

    // React/JS 피처-슬라이스(features/{X}/) 레이아웃에서 features/A 가 features/B 를 직접 IMPORT — 피처 경계 위반.
    // bulletproof-react·FSD 공통 #1 규칙: 피처는 서로 의존 금지(공유는 shared/ 경유, 연동은 상위 app/라우트). opt-out으로 의도 패턴은 ignore.
    private List<Map<String, Object>> detectCrossFeatureImport(List<Node> nodes, List<Edge> edges) {
        Map<UUID, String> nodeFilePaths = new HashMap<>();
        Map<UUID, String> nameMap = new HashMap<>();
        for (Node n : nodes) {
            nodeFilePaths.put(n.getId(), n.getFilePath() != null ? n.getFilePath() : "");
            nameMap.put(n.getId(), n.getName());
        }

        // 게이트: 서로 다른 피처 2개 이상 + 프론트(TS/JS) 언어 존재 — 피처-슬라이스 프론트엔드일 때만 적용(precision).
        Set<String> features = new HashSet<>();
        boolean hasFrontend = false;
        for (Node n : nodes) {
            String f = featureOf(nodeFilePaths.get(n.getId()));
            if (f != null) features.add(f);
            if (isFrontendLanguage(n.getLanguage())) hasFrontend = true;
        }
        if (features.size() < 2 || !hasFrontend) return List.of();
        // Redux/RTK는 features/ 를 쓰지만 피처 간 import가 정상이라 발화하면 전부 오탐 — 지문 있으면 억제(precision).
        if (isReduxStyleProject(nodeFilePaths.values())) return List.of();

        List<Map<String, Object>> warnings = new ArrayList<>();
        for (Edge e : edges) {
            if (e.getType() != EdgeType.IMPORT) continue;
            String srcPath = nodeFilePaths.getOrDefault(e.getSourceNodeId(), "");
            String tgtPath = nodeFilePaths.getOrDefault(e.getTargetNodeId(), "");
            // 테스트 코드는 여러 피처를 자유롭게 import(픽스처)라 위반 아님 — 제외
            if (isTestArtifact(srcPath, nameMap.getOrDefault(e.getSourceNodeId(), ""))) continue;
            String srcFeature = featureOf(srcPath);
            String tgtFeature = featureOf(tgtPath);
            if (srcFeature != null && tgtFeature != null && !srcFeature.equals(tgtFeature)) {
                Map<String, Object> w = new LinkedHashMap<>();
                w.put("type", "CROSS_FEATURE_IMPORT");
                w.put("severity", "HIGH");
                w.put("nodeIds", List.of(e.getSourceNodeId().toString(), e.getTargetNodeId().toString()));
                w.put("edgeIds", List.of(e.getId().toString()));
                w.put("message", "피처 경계 위반: features/" + srcFeature + " → features/" + tgtFeature
                        + " 직접 import. 피처는 서로 의존하지 않아야 합니다 — 공유 로직은 shared(components/hooks/lib)로 옮기고,"
                        + " 피처 간 연동은 상위(app/라우트)에서 조립하세요.");
                warnings.add(w);
            }
        }
        return warnings;
    }

    // 경로에서 features/{X}/ 의 피처명 X 추출 — features 직속 파일(features/x.ts)이나 미해당이면 null.
    private static String featureOf(String path) {
        if (path == null) return null;
        String p = path.replace("\\", "/");
        int idx = p.indexOf("/features/");
        int start;
        if (idx >= 0) start = idx + "/features/".length();
        else if (p.startsWith("features/")) start = "features/".length();
        else return null;
        int slash = p.indexOf('/', start);
        if (slash <= start) return null;
        return p.substring(start, slash);
    }

    // Redux/RTK 프로젝트 지문 — features/ 를 쓰지만 피처-슬라이스(bulletproof/FSD)와 정반대 규칙을 따른다.
    // RTK는 피처 간 slice import와 features→app/store(RootState·AppThunk) import가 정상이라, 두 피처 규칙을 발화하면 전부 오탐.
    // 지문: rootReducer.*(combineReducers 루트, 거의 Redux 전용)·app/store.*(RTK 정형 스토어 위치).
    // bulletproof/FSD의 app/ 은 router/provider라 store가 없어 이 지문이 없다 → recall 보존.
    private static boolean isReduxStyleProject(Collection<String> paths) {
        for (String raw : paths) {
            if (raw == null) continue;
            String p = raw.replace("\\", "/").toLowerCase();
            if (p.matches("(.*/)?rootreducer\\.(ts|tsx|js|jsx)")) return true;
            if (p.matches("(.*/)?app/store\\.(ts|tsx|js|jsx)")) return true;
        }
        return false;
    }

    // 프론트(TS/JS 계열) 언어인지 — 피처-슬라이스 게이트용(백엔드 features/ 디렉터리 오발화 방지)
    private static boolean isFrontendLanguage(String lang) {
        if (lang == null) return false;
        String l = lang.toLowerCase();
        return l.contains("typescript") || l.contains("javascript")
                || l.equals("tsx") || l.equals("jsx") || l.equals("ts") || l.equals("js");
    }

    // React/JS 피처-슬라이스 레이아웃의 레이어 단방향 위반 감지 — 하위 레이어가 상위를 import.
    // 순위 app→features→entities→shared: bulletproof(app/features/shared) + FSD entities 레이어.
    // bulletproof-react eslint import/no-restricted-paths zone 2·3: shared↛features·app, features↛app 도 이 순위로 포괄.
    // CROSS_FEATURE(같은 features 레이어 내 피처 간)와 상보적. 게이트는 CROSS_FEATURE와 동일(프론트 언어 + 피처 2개↑).
    private List<Map<String, Object>> detectFeatureLayerViolation(List<Node> nodes, List<Edge> edges) {
        Map<UUID, String> nodeFilePaths = new HashMap<>();
        Map<UUID, String> nameMap = new HashMap<>();
        for (Node n : nodes) {
            nodeFilePaths.put(n.getId(), n.getFilePath() != null ? n.getFilePath() : "");
            nameMap.put(n.getId(), n.getName());
        }

        // 게이트: 프론트(TS/JS) 언어 + 서로 다른 피처 2개 이상 — 피처-슬라이스 프론트엔드일 때만 적용(precision, CROSS_FEATURE와 동일).
        Set<String> features = new HashSet<>();
        boolean hasFrontend = false;
        for (Node n : nodes) {
            String f = featureOf(nodeFilePaths.get(n.getId()));
            if (f != null) features.add(f);
            if (isFrontendLanguage(n.getLanguage())) hasFrontend = true;
        }
        if (features.size() < 2 || !hasFrontend) return List.of();
        // Redux/RTK는 features→app/store(RootState·AppThunk) import가 정상이라 발화하면 전부 오탐 — 지문 있으면 억제(precision).
        if (isReduxStyleProject(nodeFilePaths.values())) return List.of();

        List<Map<String, Object>> warnings = new ArrayList<>();
        for (Edge e : edges) {
            if (e.getType() != EdgeType.IMPORT) continue;
            String srcPath = nodeFilePaths.getOrDefault(e.getSourceNodeId(), "");
            String tgtPath = nodeFilePaths.getOrDefault(e.getTargetNodeId(), "");
            // 테스트 코드는 레이어를 자유롭게 import(픽스처)라 위반 아님 — 제외
            if (isTestArtifact(srcPath, nameMap.getOrDefault(e.getSourceNodeId(), ""))) continue;
            int srcRank = frontendLayerRank(srcPath);
            int tgtRank = frontendLayerRank(tgtPath);
            // 둘 다 분류돼야 하고, 같은 레이어 간 import는 여기 대상 아님(피처 간은 CROSS_FEATURE가 담당)
            if (srcRank < 0 || tgtRank < 0 || srcRank == tgtRank) continue;
            // 하위(rank 큼) → 상위(rank 작음) import = 단방향 역전 위반
            if (srcRank > tgtRank) {
                Map<String, Object> w = new LinkedHashMap<>();
                w.put("type", "FEATURE_LAYER_VIOLATION");
                w.put("severity", "HIGH");
                w.put("nodeIds", List.of(e.getSourceNodeId().toString(), e.getTargetNodeId().toString()));
                w.put("edgeIds", List.of(e.getId().toString()));
                w.put("message", "레이어 단방향 위반: " + frontendLayerLabel(srcRank) + " → " + frontendLayerLabel(tgtRank)
                        + " 직접 import. 의존은 app → features → entities → shared 한 방향이어야 합니다 — "
                        + frontendLayerLabel(srcRank) + "은(는) " + frontendLayerLabel(tgtRank) + "을(를) 알면 안 됩니다."
                        + " 공통 로직은 하위 레이어로 내리고, 조립은 상위 레이어에서 하세요.");
                warnings.add(w);
            }
        }
        return warnings;
    }

    // bulletproof-react가 shared 레이어로 흩어 두는 재사용 모듈 디렉터리(FSD의 단일 shared/ 와 달리 scatter).
    // FSD 전용 레이어(pages/widgets/entities)와 shared/ 는 frontendLayerRank에서 별도 명시 판정한다.
    private static final Set<String> SHARED_DIRS = Set.of(
        "components", "hooks", "lib", "utils", "types", "stores", "config", "providers", "assets"
    );

    // 프론트 레이어 순위 — app=0 > features=1 > entities=2 > shared=3 (위가 상위, 아래가 하위). 미분류 -1.
    // 하위(rank 큼)가 상위(rank 작음)를 import하면 단방향 위반. bulletproof(app/features/shared)+FSD entities 레이어.
    // ★pages·widgets 는 의도적으로 미분류: pages/ 는 Next.js 프레임워크 라우팅(src/pages/app/...)으로도 흔히 쓰여
    //   FSD 레이어와 모호(features/ 가 RTK와 모호했던 것과 동형) → 오탐 위험. widgets/ 는 벤치 미검증이라 보류.
    //   entities 만 추가: FSD 고유 디렉터리라 모호성 낮고 entities↛features 핵심 규칙을 todo-app으로 검증.
    // features 우선 판정(피처 내부 components/ 등은 SHARED 아닌 FEATURE).
    private static int frontendLayerRank(String path) {
        if (featureOf(path) != null) return 1;
        if (path == null) return -1;
        String p = path.replace("\\", "/");
        if (hasSegment(p, "app")) return 0;
        if (hasSegment(p, "entities")) return 2;
        if (hasSegment(p, "shared")) return 3;
        for (String d : SHARED_DIRS) {
            if (hasSegment(p, d)) return 3;
        }
        return -1;
    }

    // 정규화된 경로에 디렉터리 세그먼트가 있는지(/dir/ 또는 dir/ 로 시작)
    private static boolean hasSegment(String normalizedPath, String dir) {
        return normalizedPath.contains("/" + dir + "/") || normalizedPath.startsWith(dir + "/");
    }

    private static String frontendLayerLabel(int rank) {
        return switch (rank) {
            case 0 -> "app";
            case 1 -> "features";
            case 2 -> "entities";
            case 3 -> "shared";
            default -> "?";
        };
    }

    // 아키텍처 레이어/하위패키지 용어 — 헥사고날·클린아키텍처에서 application/domain/, application/port/ 처럼
    // 레이어명이 컨텍스트 자리에 오는 것을 바운디드 컨텍스트로 오인하지 않도록 제외(buckpal 류 교과서 FP 방지).
    // shared·common·seedwork·shared_kernel·kernel 은 Shared Kernel(모든 컨텍스트가 공유하는 베이스)이라
    // 바운디드 컨텍스트가 아니다 — 이를 import하는 것은 정상이므로 컨텍스트로 인식하면 cross-context 오탐을 낸다.
    private static final Set<String> LAYER_TERMS = Set.of(
        "domain", "application", "infrastructure", "interfaces", "presentation",
        "adapter", "adapters", "port", "ports", "service", "services",
        "model", "models", "entity", "entities", "repository", "repositories",
        "controller", "controllers", "usecase", "usecases", "use_case",
        "in", "out", "web", "persistence", "api", "rest", "config",
        "common", "shared", "dto", "dtos", "mapper", "mappers", "util", "utils",
        "seedwork", "shared_kernel", "kernel"
    );

    // 도메인 레이어 디렉터리 별칭 — 실제 Java 레포는 domain 외에 core·domains 로도 도메인 레이어를 명명한다.
    // (recall 확장: 리터럴 /domain/ 만 보면 core/ 류 레이아웃에서 도메인→인프라 위반을 못 잡음.)
    private static final Set<String> DOMAIN_LAYER_DIRS = Set.of("domain", "domains", "core");
    // 인프라 레이어 디렉터리 별칭 — infrastructure 외에 infra·persistence·adapter·dao 등. 도메인→인프라는 어떤
    // 아키텍처에서도 위반(보편)이라 별칭 인식은 precision 위험이 낮다(레이어 이름만 넓힐 뿐 규칙은 불변).
    // "db"는 Python 생태계(예: app/db/repositories)의 영속화 레이어 관용 명명이다.
    private static final Set<String> INFRA_LAYER_DIRS = Set.of(
        "infrastructure", "infra", "persistence", "adapter", "adapters", "dao", "db");
    // 인터페이스(진입점) 레이어 디렉터리 별칭 — Controller/Presentation 계층. application(usecase)과는 별개로
    // 좁게 잡는다 — application→infrastructure는 정상 방향(포트/직접 호출 둘 다 허용)이라 여기 섞으면 오탐.
    private static final Set<String> INTERFACE_LAYER_DIRS = Set.of(
        "interfaces", "presentation", "controllers", "controller");
    // 애플리케이션 레이어 디렉터리 별칭 — isDddProject 게이트가 레이어드/DDD 프로젝트를 인식할 때 사용.
    // "app"은 제외 — /app/ 은 앱 루트 패키지로 흔히 쓰여(레이어 아님) 오분류를 일으킨다.
    // "services"는 Python 생태계(예: app/services)의 애플리케이션 레이어 관용 명명이다.
    private static final Set<String> APPLICATION_LAYER_DIRS = Set.of("application", "usecase", "usecases", "services");
    // DB 레이어 우회의 상위(소스) 레이어 별칭 — interfaces/application 류. 이들이 영속화 계층을 직접 import하면
    // 도메인 Repository 추상을 건너뛴 위반. presentation은 interfaces의 흔한 별칭.
    // "api"·"routes"는 Python/JS 생태계(예: app/api/routes)의 인터페이스(웹 진입) 레이어 관용 명명이다.
    private static final Set<String> UPPER_LAYER_DIRS = Set.of(
        "interfaces", "presentation", "application", "usecase", "usecases", "api", "routes");
    // 영속화(persistence) 하위 디렉터리 별칭 — DB 접근 책임을 가진 디렉터리. DB_LAYER_BYPASS는 영속화 타깃을
    // "INFRA 레이어(INFRA_LAYER_DIRS) ∩ 이 세그먼트"로 한정한다 — infrastructure/service 같은 비-영속화 디렉터리와
    // domain/port/repository 같은 도메인 인터페이스(INFRA 레이어 밖)를 제외해 precision을 지킨다.
    private static final Set<String> PERSISTENCE_LAYER_DIRS = Set.of(
        "persistence", "db", "repository", "repositories", "dao",
        "jpa", "jdbc", "mybatis", "mapper", "mappers", "orm", "datasource");

    // 파일명이 error(s)/exception(s) 모듈인지 — 언어 무관(errors.py·Errors.java·exceptions.ts 등). 이런 파일은
    // 리포지토리 CRUD가 아닌 예외 타입 정의뿐이라 persistence 계층에 있어도 "직접 호출"이 성립하지 않는다.
    private static boolean isErrorModule(String path) {
        if (path == null) return false;
        String p = path.replace("\\", "/");
        int slash = p.lastIndexOf('/');
        String base = slash >= 0 ? p.substring(slash + 1) : p;
        int dot = base.lastIndexOf('.');
        String stem = (dot > 0 ? base.substring(0, dot) : base).toLowerCase();
        return stem.equals("error") || stem.equals("errors")
                || stem.equals("exception") || stem.equals("exceptions");
    }

    // 컴포지션 루트/부트스트랩 클래스 — 애플리케이션 시작 시 구체 구현체를 배선하는 것이 설계 의도라
    // 레이어링 규칙(DB_LAYER_BYPASS)의 의도적 예외. 클래스명 접미사로 판별(DI/부트스트랩 공통 관용명).
    private static boolean isCompositionRoot(String path) {
        if (path == null) return false;
        String p = path.replace("\\", "/");
        int slash = p.lastIndexOf('/');
        String base = slash >= 0 ? p.substring(slash + 1) : p;
        int dot = base.lastIndexOf('.');
        String className = dot > 0 ? base.substring(0, dot) : base;
        return className.endsWith("LifeCycle") || className.endsWith("Lifecycle")
                || className.endsWith("Bootstrap") || className.endsWith("Configuration");
    }

    // 경로에 주어진 레이어 디렉터리 세그먼트(/{dir}/) 중 하나라도 포함되는지 — 슬래시 정규화 후 세그먼트 매칭
    private static boolean containsLayerSegment(String path, Set<String> dirs) {
        if (path == null) return false;
        String p = path.replace("\\", "/");
        for (String d : dirs) {
            if (p.contains("/" + d + "/")) return true;
        }
        return false;
    }

    // application 레이어 별칭(application/usecase 등) 바로 다음 세그먼트를 컨텍스트명으로 추출 — 레이어 용어면 null.
    private String extractContextFromApplicationPath(String path) {
        return extractContextAfterLayer(path, APPLICATION_LAYER_DIRS, false);
    }

    // domain 레이어 별칭(domain/domains/core 등) 바로 다음 세그먼트를 컨텍스트명으로 추출 — 없으면 null.
    // domain 마커가 application 마커 하위에 중첩(application/domain/model)이면 헥사고날 레이어이지 top-level 도메인
    // 레이어가 아니므로 컨텍스트로 보지 않는다. 추출 세그먼트가 레이어 용어인 경우도 제외.
    private String extractContextFromDomainPath(String path) {
        return extractContextAfterLayer(path, DOMAIN_LAYER_DIRS, true);
    }

    // 경로에서 주어진 레이어 별칭 디렉터리 바로 다음 세그먼트를 컨텍스트명으로 추출 — 레이어 용어면 다음 별칭 시도.
    // excludeNestedUnderApplication=true면 해당 마커가 application 별칭 하위에 중첩된 경우 건너뛴다.
    private String extractContextAfterLayer(String path, Set<String> layerDirs, boolean excludeNestedUnderApplication) {
        String p = path.replace("\\", "/");
        for (String layer : layerDirs) {
            String marker = "/" + layer + "/";
            int idx = p.indexOf(marker);
            if (idx < 0) continue;
            if (excludeNestedUnderApplication) {
                int appIdx = firstLayerIndex(p, APPLICATION_LAYER_DIRS);
                if (appIdx >= 0 && appIdx < idx) continue;
            }
            String after = p.substring(idx + marker.length());
            int slash = after.indexOf('/');
            if (slash <= 0) continue;
            String seg = after.substring(0, slash);
            if (LAYER_TERMS.contains(seg)) continue;
            return seg;
        }
        return null;
    }

    // 경로에서 주어진 레이어 별칭 마커(/{dir}/) 중 가장 앞선 인덱스 — 없으면 -1. 중첩 도메인 가드용.
    private static int firstLayerIndex(String p, Set<String> dirs) {
        int best = -1;
        for (String d : dirs) {
            int i = p.indexOf("/" + d + "/");
            if (i >= 0 && (best < 0 || i < best)) best = i;
        }
        return best;
    }

    // 컨텍스트 경계를 이루는 내부 레이어 디렉터리 — 한 세그먼트가 이들 중 2개 이상을 직접 선행하면 그 세그먼트는
    // 바운디드 컨텍스트(context-first 레이아웃 {context}/{layer}/)일 가능성이 높다.
    private static final Set<String> CONTEXT_BOUNDARY_LAYERS = Set.of(
        "application", "usecase", "usecases", "model", "models", "domain", "domains", "core",
        "infrastructure", "infra", "adapter", "adapters", "persistence", "web", "port", "ports",
        "interfaces", "presentation", "api", "dao");
    // context-first 레이아웃에서 도메인 레이어로 인정하는 디렉터리 — 별칭 + model(DDD 애그리거트 모델).
    // model을 전역 DOMAIN_LAYER_DIRS에 넣지 않고 여기서만 쓰는 이유: model은 흔한 일반 디렉터리라
    // 확인된 context-first 컨텍스트의 직하위일 때만 도메인으로 보아야 precision이 안전하다.
    private static final Set<String> CONTEXT_FIRST_DOMAIN_DIRS = Set.of(
        "domain", "domains", "core", "model", "models");

    // 전역 레이아웃 추론: {context}/{layer}/ 형태의 context-first 컨텍스트 집합을 반환.
    // 판별 = 한 세그먼트가 서로 다른 CONTEXT_BOUNDARY_LAYERS를 2개 이상 직접 선행하고, 그런 세그먼트가 2개 이상일 때만
    // (layer-first 레포의 패키지 루트는 단 하나만 레이어를 선행하므로 1개<2로 배제 → 무회귀).
    private Set<String> detectContextFirstContexts(Collection<String> paths) {
        Map<String, Set<String>> segToLayers = new HashMap<>();
        for (String raw : paths) {
            if (raw == null) continue;
            String[] segs = raw.replace("\\", "/").split("/");
            for (int i = 0; i + 1 < segs.length; i++) {
                String next = segs[i + 1];
                String seg = segs[i];
                if (seg.isEmpty() || LAYER_TERMS.contains(seg)) continue;
                if (CONTEXT_BOUNDARY_LAYERS.contains(next)) {
                    segToLayers.computeIfAbsent(seg, k -> new HashSet<>()).add(next);
                }
            }
        }
        Set<String> candidates = new HashSet<>();
        for (Map.Entry<String, Set<String>> e : segToLayers.entrySet()) {
            if (e.getValue().size() >= 2) candidates.add(e.getKey());
        }
        // 후보가 2개 미만이면 단일 루트(layer-first)이므로 context-first가 아니다.
        return candidates.size() >= 2 ? candidates : Set.of();
    }

    // application 컨텍스트 — context-first면 application 마커 앞 세그먼트(확인된 컨텍스트), 아니면 layer-first 추출.
    private String applicationContextOf(String path, Set<String> cfContexts) {
        if (!cfContexts.isEmpty()) {
            String cf = contextBeforeLayer(path, APPLICATION_LAYER_DIRS, cfContexts);
            if (cf != null) return cf;
        }
        return extractContextFromApplicationPath(path);
    }

    // domain 컨텍스트 — context-first면 도메인 레이어(model 포함) 마커 앞 세그먼트, 아니면 layer-first 추출.
    private String domainContextOf(String path, Set<String> cfContexts) {
        if (!cfContexts.isEmpty()) {
            String cf = contextBeforeLayer(path, CONTEXT_FIRST_DOMAIN_DIRS, cfContexts);
            if (cf != null) return cf;
        }
        return extractContextFromDomainPath(path);
    }

    // 레이어 별칭 마커(/{layer}/) 바로 앞 세그먼트가 확인된 context-first 컨텍스트면 반환 — 아니면 null.
    private String contextBeforeLayer(String path, Set<String> layerDirs, Set<String> cfContexts) {
        String p = path.replace("\\", "/");
        for (String layer : layerDirs) {
            int idx = p.indexOf("/" + layer + "/");
            if (idx < 0) continue;
            String before = p.substring(0, idx);
            int ls = before.lastIndexOf('/');
            String seg = ls >= 0 ? before.substring(ls + 1) : before;
            if (cfContexts.contains(seg)) return seg;
        }
        return null;
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
        "noneMatch", "findFirst", "toList",
        // String/Pattern 정규식 메서드 — Matcher.matches()·str.matches()·Pattern.matcher() 가 동명 도메인 함수로 오연결되는 phantom 차단
        "matches", "matcher"
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
        // 분석기는 repoRoot 상대경로를 저장 — 루트 레벨 tests/ 는 "tests/..."(앞 슬래시 없음)이라
        // 앞뒤로 슬래시를 둘러 세그먼트 단위로 매칭한다 (루트·중간 디렉터리 모두 포착).
        String guarded = "/" + fp.replace("\\", "/") + "/";
        if (guarded.contains("/test/") || guarded.contains("/tests/")
                || guarded.contains("/__tests__/")) return true;
        if (fp.endsWith("Test.java") || fp.endsWith("Tests.java")
                || fp.endsWith("Test.kt") || fp.endsWith("Tests.kt")
                || fp.endsWith(".test.ts") || fp.endsWith(".test.tsx")
                || fp.endsWith(".test.js") || fp.endsWith(".test.jsx")
                || fp.endsWith(".spec.ts") || fp.endsWith(".spec.tsx")
                || fp.endsWith(".spec.js") || fp.endsWith(".spec.jsx")
                || fp.endsWith("Test.swift") || fp.endsWith("Tests.swift")
                || fp.endsWith("_test.go") || fp.endsWith("_test.py")) return true;
        // pytest 관례 — test_ 로 시작하는 함수
        if (name.startsWith("test_")) return true;
        return false;
    }

    // HIGH_FAN_OUT 조율자 예외 — Controller/ApplicationService/Facade(백엔드), 페이지 합성 루트 *Inner(프론트)는
    // 여러 협력자를 모아 조립하는 게 본연의 역할이라 fan-out이 높은 게 정상이다(단일 책임 위반 신호 아님).
    private boolean isOrchestratorArtifact(String fp, String name) {
        String normalized = fp.replace("\\", "/");
        if (normalized.endsWith("Controller.java")) return true;
        if (normalized.contains("/application/") && (normalized.endsWith("ApplicationService.java") || normalized.endsWith("Facade.java"))) return true;
        return name != null && name.endsWith("Inner");
    }

    // DEAD_CODE 신뢰도 게이트 — 미호출 함수 비율이 이 값을 넘으면 호출 추출 자체가 불완전하다고 보고 개별 경고를 생략한다.
    // 재캘리브레이션(2026-06-17, production-parity 측정): LocalAnalyzer↔GraphBuilder 정렬 후 신뢰 가능한 수치로 재측정.
    //   정상 앱/DDD: petclinic 0%·gin 0.1%·codeprint 0.1%. 약-추출 라이브러리: requests 5.3%(38건, 전부 Python
    //   duck-typing·동적 디스패치 오탐)·express 21%. 기존 15%는 requests를 못 잡아 38건 오탐 노출 → 4%로 하향
    //   (정상 클러스터 ≤0.1% 대비 40배 마진, requests 5.3% 대비 1.3%p 마진으로 두 약-추출 레포 모두 게이트).
    private static final double DEAD_CODE_UNTRUSTWORTHY_RATIO = 0.04;
    // 함수 수가 적으면 비율이 통계적으로 불안정 — 소형 그래프는 게이트 미적용 (기존 미호출 함수 1~2개 케이스 보호)
    private static final int DEAD_CODE_MIN_FUNCTIONS = 30;
    // 미호출 함수 절대 개수 하한 — 비율만으로는 "소형 레포의 소수 진짜 데드코드"와 "대형 레포의 다수 오탐"을 구분 못 함.
    // 비율을 4%로 낮추면 진짜 데드 함수 몇 개뿐인 레포까지 게이트되므로, 개수 하한으로 약-추출 신호(다수 오탐)만 포착한다.
    private static final int DEAD_CODE_MIN_GATE_COUNT = 10;

    // FUNCTION 노드 중 아무 FUNCTION_CALL 엣지도 받지 않는 함수 — 데드 코드 후보
    // 아래 5가지 패턴은 정적 분석으로 호출 추적이 불가능하여 false positive 발생:
    //   1. JSX 렌더 (<App />) — React.createElement 호출, FUNCTION_CALL 엣지로 연결 안 됨
    //   2. JPA Repository 메서드 — Spring AOP 프록시가 런타임에 호출
    //   3. DDD 팩토리 메서드(of/create) — import 후 다른 파일에서 사용, cross-file 추적 미완성
    //   4. 콜백 참조(addEventListener(handler)) — 값으로 전달, "호출"이 아님 (referencedAsValue 메타로 제외, B-16)
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

        // 함수명별 정의 개수 — 동명 함수가 여러 파일에 정의되면 인터페이스/리시버 다형성 디스패치 신호
        Map<String, Integer> defCountByName = new HashMap<>();
        for (Node n : nodes) {
            if (n.getType() == NodeType.FUNCTION && n.getName() != null) {
                defCountByName.merge(n.getName(), 1, Integer::sum);
            }
        }

        List<Map<String, Object>> warnings = new ArrayList<>();
        for (Node n : nodes) {
            if (n.getType() != NodeType.FUNCTION) continue;
            if (calledFuncIds.contains(n.getId())) continue;

            String fp = n.getFilePath() != null ? n.getFilePath() : "";
            String name = n.getName() != null ? n.getName() : "";
            // 디렉터리 세그먼트 매칭용 — 분석 루트가 해당 디렉터리 자체면 경로가 "components/x"처럼 앞 슬래시가 없어
            // fp.contains("/components/")가 빗나간다. 앞에 "/"를 붙여 루트 레벨 디렉터리도 세그먼트로 매칭한다
            // (isTestArtifact의 슬래시 래핑과 동일 원리). 데스크탑/서브디렉터리 분석에서 React·레이어 제외가 깨지던 버그.
            String fpSeg = "/" + fp;

            // Python 던더 메서드(__init__·__iter__ 등) — 런타임이 호출, 이름으로 불리지 않음
            if (name.length() > 4 && name.startsWith("__") && name.endsWith("__")) continue;
            // 테스트 코드 제외 (경로·파일명·함수명 패턴)
            if (isTestArtifact(fp, name)) continue;
            // interfaces/ 레이어 — 컨트롤러, WebSocket 핸들러 등 외부 진입점
            if (fpSeg.contains("/interfaces/")) continue;
            // React 컴포넌트 — .tsx 파일에서 대문자 시작 함수 (JSX로 렌더링되므로 FUNCTION_CALL 엣지 없음)
            if ((fp.endsWith(".tsx") || fp.endsWith(".jsx")) && !name.isEmpty()
                    && Character.isUpperCase(name.charAt(0))) continue;
            // pages/ · components/ · hooks/ · utils/ · lib/ 레이어 — React 모듈 전체가 export 기반
            if (fpSeg.contains("/pages/") || fpSeg.contains("/components/") || fpSeg.contains("/hooks/")
                    || fpSeg.contains("/utils/") || fpSeg.contains("/lib/")) continue;
            // JPA Repository 구현체 · domain 팩토리 메서드 등 프레임워크 호출 패턴
            if (FRAMEWORK_CALL_NAMES.contains(name)) continue;
            // getter/setter/onXxx/handleXxx 네이밍 패턴 — 프레임워크·Lombok 자동 생성
            if (isFrameworkCallPattern(name)) continue;
            // application/ 레이어 — Spring @Service 메서드는 DI를 통해 호출, FUNCTION_CALL 엣지 없음
            if (fpSeg.contains("/application/")) continue;
            // infrastructure/ 레이어 — Spring @Bean, @EventListener, Filter 등 프레임워크 진입점 다수
            if (fpSeg.contains("/infrastructure/")) continue;
            // domain/ Repository·Port 인터페이스 선언 메서드 — 구현체가 인터페이스를 통해 호출(다형성 디스패치).
            // 같은 이름의 FUNCTION_CALL이 존재하면 사용 중으로 간주 (미호출이면 여전히 데드 코드로 감지).
            boolean isDomainInterfaceDecl = fpSeg.contains("/domain/")
                    && (fp.endsWith("Repository.java") || fp.endsWith("Port.java") || fpSeg.contains("/port/"));
            if (isDomainInterfaceDecl && calledFuncNames.contains(name)) continue;
            // 동명 함수가 2개 이상 정의되고 그 이름으로 호출이 존재 — 인터페이스/리시버 다형성 디스패치로 간주.
            // 정적 분석은 호출을 한 구현체로만 연결하므로 나머지 구현체가 거짓 데드코드로 보이는 것을 방지.
            // (Go func (T) Bind()·Name() 등 다중 구현. 단일 정의 미호출은 여전히 감지 — 과잉 억제 방지.)
            if (defCountByName.getOrDefault(name, 0) >= 2 && calledFuncNames.contains(name)) continue;

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
                // 값(콜백·고차함수 인자)으로 참조되는 함수 — 호출 엣지는 없지만 사용 중 (B-16)
                if (Boolean.TRUE.equals(meta.get("referencedAsValue"))) continue;
            }

            Map<String, Object> w = new LinkedHashMap<>();
            w.put("type", "DEAD_CODE");
            w.put("severity", "LOW");
            w.put("nodeIds", List.of(n.getId().toString()));
            w.put("edgeIds", List.of());
            w.put("message", "데드 코드 후보: " + name
                    + " — 이 함수를 호출하는 곳이 없습니다. DI·리플렉션·직렬화로 사용 중이면 suppress. 실제 미사용이면 삭제를 검토하세요.");
            warnings.add(w);
        }

        // 신뢰도 게이트 — 미호출 비율이 임계를 넘으면 호출 추출이 약한 것이므로 개별 경고 대신 단일 안내로 치환
        long totalFunctions = nodes.stream().filter(n -> n.getType() == NodeType.FUNCTION).count();
        if (totalFunctions >= DEAD_CODE_MIN_FUNCTIONS
                && warnings.size() >= DEAD_CODE_MIN_GATE_COUNT
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

            // 테스트 코드가 인프라를 직접 import하는 것은 정상이라 도메인→인프라 위반 아님 — 제외
            if (isTestArtifact(srcPath, nameMap.getOrDefault(e.getSourceNodeId(), ""))) continue;

            boolean srcIsDomain = containsLayerSegment(srcPath, DOMAIN_LAYER_DIRS);
            boolean tgtIsInfra = containsLayerSegment(tgtPath, INFRA_LAYER_DIRS) && !tgtPath.contains("/shared/");

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

    // interfaces/(Controller 등)가 infrastructure/ 를 직접 IMPORT — 의존 방향 위반
    // (Interfaces → Application → Domain 단방향, Interfaces가 Infrastructure를 건너뛰면 안 됨)
    // 컴포지션 루트(*Config·*Configuration·*Bootstrap·*LifeCycle)는 배선이 설계 의도라 예외 — DB_LAYER_BYPASS와 동일 판단.
    // severity=MEDIUM: 도입 초기 관찰 기간(자기 레포 0건·벤치 무오탐 확인 후 HIGH 승격 검토).
    private List<Map<String, Object>> detectInterfaceInfraImport(List<Node> nodes, List<Edge> edges) {
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

            if (isTestArtifact(srcPath, nameMap.getOrDefault(e.getSourceNodeId(), ""))) continue;
            if (isCompositionRoot(srcPath)) continue;

            boolean srcIsInterface = containsLayerSegment(srcPath, INTERFACE_LAYER_DIRS);
            boolean tgtIsInfra = containsLayerSegment(tgtPath, INFRA_LAYER_DIRS) && !tgtPath.contains("/shared/");

            if (srcIsInterface && tgtIsInfra) {
                Map<String, Object> w = new LinkedHashMap<>();
                w.put("type", "INTERFACES_IMPORTS_INFRA");
                w.put("severity", "MEDIUM");
                w.put("nodeIds", List.of(e.getSourceNodeId().toString(), e.getTargetNodeId().toString()));
                w.put("edgeIds", List.of(e.getId().toString()));
                w.put("message", "레이어 단방향 위반: interfaces/ 가 infrastructure/ 를 직접 import. "
                        + "Application Service나 Facade를 경유하세요(Interfaces → Application → Domain ← Infrastructure).");
                warnings.add(w);
            }
        }
        return warnings;
    }

    // detectDomainInfraImport/detectInterfaceInfraImport와 동일 판정 로직 재사용 — detectLayeredViolations 중복 가드용
    private boolean coveredByUniversalDependencyRule(String srcPath, String tgtPath) {
        boolean tgtIsInfra = containsLayerSegment(tgtPath, INFRA_LAYER_DIRS) && !tgtPath.contains("/shared/");
        if (!tgtIsInfra) return false;
        return containsLayerSegment(srcPath, DOMAIN_LAYER_DIRS) || containsLayerSegment(srcPath, INTERFACE_LAYER_DIRS);
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

        // 컨텍스트 추출은 CROSS_CONTEXT_IMPORT와 동일한 레이어-인지 추출기(레이어 용어 스킵·application 하위 중첩 가드)를 쓴다 —
        // 헥사고날 단일 컨텍스트(application/domain/{service,model})를 별개 도메인으로 오인식하던 FP를 제거(buckpal 16건).
        Set<String> cfContexts = detectContextFirstContexts(nodeFilePaths.values());

        // 함수명 → 등장 도메인 집합 — 동일 이름이 2개 이상 도메인에 있으면 bare-name 해석이 모호 → 오탐 제외용
        Map<String, Set<String>> funcNameToDomains = new HashMap<>();
        for (Node n : nodes) {
            if (n.getType() != NodeType.FUNCTION) continue;
            String domain = functionContextOf(n.getFilePath() != null ? n.getFilePath() : "", cfContexts);
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
            // 테스트 코드는 아키텍처 위반 대상이 아님 — isTestPath(Java src/test·JS test/spec 한정)는 pytest
            // tests/·test_*.py 관례를 못 걸러 Python DDD 레포에서 대량 오탐(다른 검출기와 동일한 isTestArtifact로 통일)
            if (isTestArtifact(srcPath, nameMap.getOrDefault(e.getSourceNodeId(), ""))
                    || isTestArtifact(tgtPath, nameMap.getOrDefault(e.getTargetNodeId(), ""))) continue;

            String srcDomain = functionContextOf(srcPath, cfContexts);
            String tgtDomain = functionContextOf(tgtPath, cfContexts);

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

    // 의도↔실제 conformance — 사용자가 선언한 모듈 금지 의존 규칙을 실제 IMPORT 엣지가 어기는지 검사
    // 수정 방법: 의존을 제거(port/adapter 역전 등)하거나, 의도가 바뀌었으면 선언 규칙을 갱신
    // IMPORT 엣지만 본다 — 모듈 의존은 소스 레벨 import가 정답 신호. FUNCTION_CALL은 인터페이스→구현체 해소
    // (port→adapter는 정당한 의존성 역전)와 bare-name 퍼지매칭으로 오탐을 만들어 제외(measurement로 확정).
    private List<Map<String, Object>> detectIntentDrift(List<Node> nodes, List<Edge> edges, ArchitectureIntent intent) {
        if (intent == null || intent.isEmpty()) return List.of();

        Map<UUID, String> nodeFilePaths = new HashMap<>();
        for (Node n : nodes) {
            nodeFilePaths.put(n.getId(), n.getFilePath() != null ? n.getFilePath() : "");
        }

        List<Map<String, Object>> warnings = new ArrayList<>();
        // 같은 (from모듈,to모듈,소스파일,타깃파일) 위반의 중복 엣지를 한 경고로 합침
        Set<String> seen = new HashSet<>();
        for (Edge e : edges) {
            if (e.getType() != EdgeType.IMPORT) continue;
            String srcPath = nodeFilePaths.getOrDefault(e.getSourceNodeId(), "");
            String tgtPath = nodeFilePaths.getOrDefault(e.getTargetNodeId(), "");
            if (srcPath.isEmpty() || tgtPath.isEmpty()) continue;

            String srcModule = intent.moduleOf(srcPath);
            String tgtModule = intent.moduleOf(tgtPath);
            if (srcModule == null || tgtModule == null || srcModule.equals(tgtModule)) continue;
            if (!intent.isForbidden(srcModule, tgtModule)) continue;

            String key = srcModule + ">" + tgtModule + ">" + srcPath + ">" + tgtPath;
            if (!seen.add(key)) continue;

            Map<String, Object> w = new LinkedHashMap<>();
            w.put("type", "INTENT_DRIFT");
            w.put("severity", "HIGH");
            w.put("nodeIds", List.of(e.getSourceNodeId().toString(), e.getTargetNodeId().toString()));
            w.put("edgeIds", List.of(e.getId().toString()));
            w.put("message", "의도 위반: 모듈 '" + srcModule + "' → '" + tgtModule
                    + "' 의존 금지 (" + fileName(srcPath) + " → " + fileName(tgtPath)
                    + "). 수정: 의존을 제거하거나(port/adapter 역전 등) 선언한 아키텍처 규칙을 갱신하세요.");
            warnings.add(w);
        }
        return warnings;
    }

    // 경로에서 파일명만 추출 (메시지 표시용)
    private static String fileName(String path) {
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
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
    // 함수 파일의 바운디드 컨텍스트 — domain/application 레이어-인지 추출기로 해소(레이어 용어·application 하위 중첩 가드 포함).
    // 헥사고날 단일 컨텍스트(application/domain/service 등)는 null → cross-domain 오탐 방지. layer-first({layer}/{context})는 그대로 컨텍스트 추출.
    private String functionContextOf(String path, Set<String> cfContexts) {
        String dc = domainContextOf(path, cfContexts);
        if (dc != null) return dc;
        return applicationContextOf(path, cfContexts);
    }

    // 엣지가 같은 파일 내 호출(sameFile 마커)인지 여부
    private boolean isSameFileEdge(Edge e) {
        Map<String, Object> meta = e.getMetadata();
        return meta != null && Boolean.TRUE.equals(meta.get("sameFile"));
    }

    // FUNCTION 노드가 7개 초과 FUNCTION_CALL 아웃바운드를 가질 때 — 과도한 책임 (High Fan-Out)
    private List<Map<String, Object>> detectHighFanOut(List<Node> nodes, List<Edge> edges) {
        final int THRESHOLD = 7;

        Map<UUID, Integer> fanOutMap = new HashMap<>();
        Map<UUID, String> nameMap = new HashMap<>();
        Map<UUID, String> filePathMap = new HashMap<>();
        for (Node n : nodes) {
            if (n.getType() == NodeType.FUNCTION) {
                fanOutMap.put(n.getId(), 0);
                nameMap.put(n.getId(), n.getName());
                filePathMap.put(n.getId(), n.getFilePath() != null ? n.getFilePath() : "");
            }
        }
        for (Edge e : edges) {
            // 같은 파일 내 호출(sameFile)은 fan-out 책임 과다와 무관 — 제외해 경고량 보존
            if (e.getType() == EdgeType.FUNCTION_CALL && fanOutMap.containsKey(e.getSourceNodeId())
                    && !isSameFileEdge(e)) {
                fanOutMap.merge(e.getSourceNodeId(), 1, Integer::sum);
            }
        }

        // 한 파일 내 동명 메서드(예: Go render 패키지의 여러 Render)가 한 노드로 합쳐지면 호출이 union 되어 fan-out이
        // 부풀려진다. 이런 머지 노드(mergedDefCount≥2)는 단일 책임 신호가 아니라 다형성 디스패치 아티팩트이므로 제외한다.
        // 정밀 가드 — 서로 다른 파일의 동명 함수는 각자 별도 노드(mergedDefCount 없음)라 영향받지 않는다(전역 이름
        // 휴리스틱이 일으키던 과잉 억제 해소). 한 파일 내 머지가 전역 유일이어도 정확히 잡는다(전역 휴리스틱의 누락 해소).
        Map<UUID, Integer> mergedDefCountMap = new HashMap<>();
        Set<UUID> testNodeIds = new HashSet<>();
        for (Node n : nodes) {
            if (n.getType() == NodeType.FUNCTION && n.getMetadata() != null) {
                if (n.getMetadata().get("mergedDefCount") instanceof Number num) {
                    mergedDefCountMap.put(n.getId(), num.intValue());
                }
                // 인라인 테스트 함수(Rust #[test] 등 파일명으로 못 거르는 것) — 테스트는 setup으로 호출이 많아 단일 책임 위반 아님
                if (Boolean.TRUE.equals(n.getMetadata().get("isTest"))) {
                    testNodeIds.add(n.getId());
                }
            }
        }

        List<Map<String, Object>> warnings = new ArrayList<>();
        for (Map.Entry<UUID, Integer> entry : fanOutMap.entrySet()) {
            if (entry.getValue() <= THRESHOLD) continue;
            String fnName = nameMap.get(entry.getKey());
            // 테스트 함수(Test*·_test.go·*Test.java 등)는 setup+assert로 자연히 호출이 많음 — 단일 책임 위반 아님
            if (isTestArtifact(filePathMap.getOrDefault(entry.getKey(), ""), fnName)) continue;
            // 인라인 테스트(Rust #[test] 등) — 파일명/이름 패턴으로 못 걸러 노드 메타(isTest)로 제외
            if (testNodeIds.contains(entry.getKey())) continue;
            // 진입점 main — 부트스트랩(Spring main·CLI main·func main 등)은 본질적으로 여러 협력자를 호출하므로
            // 단일 책임 위반이 아니다. "조율자를 SRP 위반으로 부르는" 과탐을 막는다(테스트 함수 제외와 동일 원리).
            if ("main".equals(fnName)) continue;
            // 조율자(오케스트레이터) — Controller/ApplicationService/Facade와 프론트 페이지 합성 루트(*Inner)는
            // 여러 협력자를 모아 조립하는 게 본연의 역할이라 fan-out이 자연히 높다. main 예외와 동일 원리를 확장.
            if (isOrchestratorArtifact(filePathMap.getOrDefault(entry.getKey(), ""), fnName)) continue;
            // 파일 내 동명 머지 노드 — union된 fan-out이라 단일 책임 신호 아님(정밀 가드: 노드별 머지 다중도)
            if (mergedDefCountMap.getOrDefault(entry.getKey(), 1) >= 2) continue;
            Map<String, Object> w = new LinkedHashMap<>();
            w.put("type", "HIGH_FAN_OUT");
            w.put("severity", "LOW");
            w.put("nodeIds", List.of(entry.getKey().toString()));
            w.put("edgeIds", List.of());
            w.put("message", "과도한 의존: " + nameMap.getOrDefault(entry.getKey(), entry.getKey().toString())
                    + " — " + entry.getValue() + "개 함수를 호출 (단일 책임 원칙 위반 가능성)."
                    + " 수정: 관련 의존성을 묶어 Helper/Facade로 추출하거나 책임별로 메서드를 분리하세요.");
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
                    + " — 기존 평문 데이터에 대한 Flyway 마이그레이션이 필요합니다."
                    + " 수정: 기존 행을 새 형식으로 변환하는 V{N}__migrate_{table}.sql 을 작성하세요.");
            warnings.add(w);
        }
        return warnings;
    }

    // 비DDD 레이어드 아키텍처의 레이어 — 의존은 상위(낮은 ordinal)→하위(높은 ordinal) 방향만 정상.
    // MODEL은 최하위 — 도메인 모델/엔티티는 어떤 상위 레이어도 알면 안 됨(순수 데이터·규칙).
    private enum Layer { CONTROLLER, SERVICE, REPOSITORY, MODEL }

    // 클래스명 접미사로 분류 못 할 때 사용하는 레이어 디렉터리 세그먼트 (언어 무관 컨벤션, 소문자)
    // "resources"는 정적 리소스 디렉터리와 충돌하므로 제외 — controller는 클래스명 접미사로 주로 잡힌다.
    private static final Set<String> CONTROLLER_DIRS =
            Set.of("controller", "controllers", "web", "rest", "api", "endpoint", "endpoints", "route", "routes");
    private static final Set<String> SERVICE_DIRS =
            Set.of("service", "services", "usecase", "usecases", "business", "biz");
    private static final Set<String> REPOSITORY_DIRS =
            Set.of("repository", "repositories", "dao", "mapper", "mappers", "persistence");
    // 도메인 모델/엔티티 — 단일 "domain" 디렉터리는 비DDD 레이어드의 모델 폴더(DDD 멀티레이어는 isDddProject가 먼저 가로챔)
    private static final Set<String> MODEL_DIRS =
            Set.of("model", "models", "entity", "entities", "domain");

    // 파일 경로를 레이어로 분류 — 클래스명 접미사(Java/C#/TS) 우선, 없으면 디렉터리 세그먼트. 미분류면 null
    private Layer classifyLayer(String filePath) {
        if (filePath == null || filePath.isEmpty()) return null;
        String path = filePath.replace("\\", "/");
        String base = path.substring(path.lastIndexOf('/') + 1);
        int dot = base.lastIndexOf('.');
        String className = dot > 0 ? base.substring(0, dot) : base;
        // 1) 클래스명 접미사 — PascalCase 컨벤션(OwnerController·OwnerRepository·OwnerService·OwnerEntity)
        if (className.endsWith("Controller")) return Layer.CONTROLLER;
        if (className.endsWith("Repository") || className.endsWith("Dao")
                || className.endsWith("DAO") || className.endsWith("Mapper")) return Layer.REPOSITORY;
        if (className.endsWith("Service")) return Layer.SERVICE;
        // Entity/Model/VO 접미사 — DTO는 레이어 전반을 오가므로(컨트롤러↔서비스 전송) 제외
        if (className.endsWith("Entity") || className.endsWith("Model") || className.endsWith("VO")) return Layer.MODEL;
        // 2) 디렉터리 세그먼트 — 앞뒤 슬래시로 세그먼트 단위 매칭(루트 레벨 디렉터리 포함)
        String guarded = "/" + path.toLowerCase() + "/";
        if (containsSegment(guarded, CONTROLLER_DIRS)) return Layer.CONTROLLER;
        if (containsSegment(guarded, REPOSITORY_DIRS)) return Layer.REPOSITORY;
        if (containsSegment(guarded, SERVICE_DIRS)) return Layer.SERVICE;
        if (containsSegment(guarded, MODEL_DIRS)) return Layer.MODEL;
        return null;
    }

    // guarded 경로("/a/b/c/")에 디렉터리 세그먼트 중 하나가 포함되는지
    private boolean containsSegment(String guardedLowerPath, Set<String> dirs) {
        for (String d : dirs) {
            if (guardedLowerPath.contains("/" + d + "/")) return true;
        }
        return false;
    }

    // 레이어명 한글 표시용 — 메시지 출력
    private String layerLabel(Layer l) {
        return switch (l) {
            case CONTROLLER -> "Controller";
            case SERVICE -> "Service";
            case REPOSITORY -> "Repository";
            case MODEL -> "Model";
        };
    }

    // 비DDD 레이어드 프로젝트의 레이어 위반 감지 — IMPORT 엣지 기반(정규식 호출 오추적 회피)
    //   1. LAYERED_REVERSE_DEPENDENCY: 하위 레이어가 상위를 import (레이어 순서 역전) — 항상 위반
    //   2. LAYERED_BYPASS: Controller가 Repository를 직접 import — Service 레이어가 존재할 때만(우회로 판단)
    // 게이팅: 분류된 레이어가 2종 이상이어야 "레이어드 프로젝트"로 인정(단순 앱·평면 구조 오탐 방지)
    private List<Map<String, Object>> detectLayeredViolations(List<Node> nodes, List<Edge> edges) {
        // FSD/피처-슬라이스 프론트엔드 게이트 — CONTROLLER_DIRS의 "api" 별칭이 FSD shared/api(공유 API 클라이언트,
        // 최하위 레이어)를 백엔드 Controller로 오분류해 entities→shared/api를 "레이어 역전"으로 오탐(fsd-examples
        // 실측, 2026-07-01). 이런 프로젝트는 전용 FEATURE_LAYER_VIOLATION(app→features→entities→shared 의미를
        // 정확히 앎)이 이미 커버하므로 동일 게이트(프론트 언어+피처 2개↑)로 이 검출기를 스킵한다.
        if (isFeatureSliceProject(nodes)) return List.of();

        Map<UUID, Layer> nodeLayer = new HashMap<>();
        Map<UUID, String> nameMap = new HashMap<>();
        Map<UUID, String> nodeFilePaths = new HashMap<>();
        EnumSet<Layer> present = EnumSet.noneOf(Layer.class);
        for (Node n : nodes) {
            if (n.getType() != NodeType.FILE) continue;
            String fp = n.getFilePath() != null ? n.getFilePath() : "";
            // 테스트 코드는 아키텍처 위반 대상이 아님
            if (isTestPath(fp) || isTestArtifact(fp, n.getName() != null ? n.getName() : "")) continue;
            Layer layer = classifyLayer(fp);
            if (layer == null) continue;
            nodeLayer.put(n.getId(), layer);
            nameMap.put(n.getId(), n.getName());
            nodeFilePaths.put(n.getId(), fp);
            present.add(layer);
        }
        // 레이어가 2종 미만이면 레이어드 아키텍처로 보지 않음 — 게이트
        if (present.size() < 2) return List.of();
        boolean hasService = present.contains(Layer.SERVICE);

        List<Map<String, Object>> warnings = new ArrayList<>();
        for (Edge e : edges) {
            if (e.getType() != EdgeType.IMPORT) continue;
            Layer src = nodeLayer.get(e.getSourceNodeId());
            Layer tgt = nodeLayer.get(e.getTargetNodeId());
            if (src == null || tgt == null || src == tgt) continue;
            // 공통 게이트(DOMAIN_IMPORTS_INFRA/INTERFACES_IMPORTS_INFRA)가 이미 잡는 엣지는 중복 라벨링하지 않음 —
            // INFRA_LAYER_DIRS가 REPOSITORY_DIRS와 persistence/dao 별칭을 공유해 같은 엣지가 두 규칙에 겹칠 수 있음.
            if (coveredByUniversalDependencyRule(nodeFilePaths.getOrDefault(e.getSourceNodeId(), ""),
                    nodeFilePaths.getOrDefault(e.getTargetNodeId(), ""))) continue;

            String srcName = nameMap.getOrDefault(e.getSourceNodeId(), "?");
            String tgtName = nameMap.getOrDefault(e.getTargetNodeId(), "?");

            if (src.ordinal() > tgt.ordinal()) {
                // 하위 레이어 → 상위 레이어 import (역전)
                Map<String, Object> w = new LinkedHashMap<>();
                w.put("type", "LAYERED_REVERSE_DEPENDENCY");
                w.put("severity", "HIGH");
                w.put("nodeIds", List.of(e.getSourceNodeId().toString(), e.getTargetNodeId().toString()));
                w.put("edgeIds", List.of(e.getId().toString()));
                w.put("message", "레이어 역전 의존: " + layerLabel(src) + " '" + srcName + "' → "
                        + layerLabel(tgt) + " '" + tgtName + "' 직접 import. "
                        + "하위 레이어는 상위 레이어를 알면 안 됩니다. 의존을 인터페이스로 역전하거나 호출 위치를 옮기세요.");
                warnings.add(w);
            } else if (hasService && src == Layer.CONTROLLER && tgt == Layer.REPOSITORY) {
                // Service 레이어가 존재하는데 Controller가 Repository를 직접 접근 (우회)
                Map<String, Object> w = new LinkedHashMap<>();
                w.put("type", "LAYERED_BYPASS");
                w.put("severity", "MEDIUM");
                w.put("nodeIds", List.of(e.getSourceNodeId().toString(), e.getTargetNodeId().toString()));
                w.put("edgeIds", List.of(e.getId().toString()));
                w.put("message", "Service 레이어 우회: Controller '" + srcName + "' → Repository '" + tgtName
                        + "' 직접 접근. 데이터 접근을 Service 레이어로 위임해 비즈니스 로직을 한곳에 모으세요.");
                warnings.add(w);
            }
        }
        return warnings;
    }
}
