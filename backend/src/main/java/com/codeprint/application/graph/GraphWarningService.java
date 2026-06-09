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
        return warnings;
    }

    // IMPORT 엣지에서 순환 의존 탐지 (DFS 사이클 검출)
    private List<Map<String, Object>> detectCyclicImports(List<Node> nodes, List<Edge> edges) {
        Map<UUID, Set<UUID>> adj = new HashMap<>();
        Map<UUID, String> nameMap = new HashMap<>();

        for (Node n : nodes) {
            if (n.getType() == NodeType.FILE) {
                adj.put(n.getId(), new HashSet<>());
                nameMap.put(n.getId(), n.getName());
            }
        }
        for (Edge e : edges) {
            if (e.getType() == EdgeType.IMPORT && adj.containsKey(e.getSourceNodeId())) {
                adj.get(e.getSourceNodeId()).add(e.getTargetNodeId());
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
            Map<String, Object> w = new LinkedHashMap<>();
            w.put("type", "CYCLIC_IMPORT");
            w.put("nodeIds", cycle.stream().map(UUID::toString).toList());
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
                w.put("message", "인터페이스 체인 끊김: " + n.getName() + " — 구현체 메서드로 가는 엣지 없음");
                warnings.add(w);
            }
        }
        return warnings;
    }
}
