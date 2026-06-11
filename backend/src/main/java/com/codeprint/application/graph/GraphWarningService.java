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
        warnings.addAll(detectDbLayerBypass(nodes, edges));
        warnings.addAll(detectCrossContextDomainImport(nodes, edges));
        warnings.addAll(detectMissingConverterMigration(nodes));
        warnings.addAll(detectDeadCode(nodes, edges));
        warnings.addAll(detectHighFanOut(nodes, edges));
        return warnings;
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
    // FUNCTION_CALL 엣지는 Tree-sitter가 인터페이스 호출을 구현체로 오추적하므로 제외
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

    // FUNCTION 노드 중 아무 FUNCTION_CALL 엣지도 받지 않는 함수 — 데드 코드 후보
    // 외부 진입점(컨트롤러 레이어, @Async, 생성자, main, 테스트)은 제외
    private List<Map<String, Object>> detectDeadCode(List<Node> nodes, List<Edge> edges) {
        Set<UUID> calledFuncIds = new HashSet<>();
        for (Edge e : edges) {
            if (e.getType() == EdgeType.FUNCTION_CALL) {
                calledFuncIds.add(e.getTargetNodeId());
            }
        }

        List<Map<String, Object>> warnings = new ArrayList<>();
        for (Node n : nodes) {
            if (n.getType() != NodeType.FUNCTION) continue;
            if (calledFuncIds.contains(n.getId())) continue;

            String fp = n.getFilePath() != null ? n.getFilePath() : "";
            String name = n.getName();

            // 테스트 코드 제외
            if (fp.contains("/test/") || fp.contains("\\test\\")) continue;
            // interfaces/ 레이어 (컨트롤러, WebSocket 핸들러 등) — 외부 진입점
            if (fp.contains("/interfaces/")) continue;
            // 생성자·main·오버라이드 후보 제외
            if ("main".equals(name) || "생성자".equals(name) || "toString".equals(name)
                    || "equals".equals(name) || "hashCode".equals(name)) continue;

            Map<String, Object> meta = n.getMetadata();
            if (meta != null) {
                // @Async 메서드는 Spring이 직접 호출 — 제외
                if (Boolean.TRUE.equals(meta.get("isAsync"))) continue;
                if (Boolean.TRUE.equals(meta.get("isConstructor"))) continue;
            }

            Map<String, Object> w = new LinkedHashMap<>();
            w.put("type", "DEAD_CODE");
            w.put("nodeIds", List.of(n.getId().toString()));
            w.put("edgeIds", List.of());
            w.put("message", "데드 코드 후보: " + name + " — 이 함수를 호출하는 곳이 없습니다");
            warnings.add(w);
        }
        return warnings;
    }

    // FUNCTION 노드가 10개 초과 FUNCTION_CALL 아웃바운드를 가질 때 — 과도한 책임 (High Fan-Out)
    private List<Map<String, Object>> detectHighFanOut(List<Node> nodes, List<Edge> edges) {
        final int THRESHOLD = 10;

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
            w.put("nodeIds", List.of(n.getId().toString()));
            w.put("edgeIds", List.of());
            w.put("message", "@Convert 컨버터 감지: " + n.getName()
                    + " — 기존 평문 데이터에 대한 Flyway 마이그레이션이 필요합니다");
            warnings.add(w);
        }
        return warnings;
    }
}
