// 두 그래프 버전의 노드·엣지 변경 사항을 계산하는 서비스
package com.codeprint.application.graph;

import com.codeprint.domain.graph.Edge;
import com.codeprint.domain.graph.Node;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GraphDiffService {

    private final GraphQueryService graphQueryService;

    public record NodeDiff(Node node, String status) {}
    public record EdgeDiff(Edge edge, String status, String sourceName, String targetName) {}
    public record DiffResult(List<NodeDiff> nodes, List<EdgeDiff> edges) {}

    // 두 graphId의 노드·엣지 diff를 계산하여 반환
    public DiffResult diff(UUID fromGraphId, UUID toGraphId) {
        List<Node> fromNodes = graphQueryService.getNodes(fromGraphId);
        List<Node> toNodes = graphQueryService.getNodes(toGraphId);
        List<Edge> fromEdges = graphQueryService.getEdges(fromGraphId);
        List<Edge> toEdges = graphQueryService.getEdges(toGraphId);

        Map<String, Node> fromNodeMap = index(fromNodes, this::nodeKey);
        Map<String, Node> toNodeMap = index(toNodes, this::nodeKey);

        List<NodeDiff> nodeDiffs = new ArrayList<>();
        toNodeMap.forEach((key, node) ->
                nodeDiffs.add(new NodeDiff(node, fromNodeMap.containsKey(key) ? "unchanged" : "added")));
        fromNodeMap.forEach((key, node) -> {
            if (!toNodeMap.containsKey(key)) nodeDiffs.add(new NodeDiff(node, "removed"));
        });

        // 엣지 diff — edgeIdentifier 기준
        Map<String, Edge> fromEdgeMap = index(fromEdges, Edge::getEdgeIdentifier);
        Map<String, Edge> toEdgeMap = index(toEdges, Edge::getEdgeIdentifier);

        // toGraph 노드 이름 조회용 맵
        Map<UUID, String> toNodeNameMap = toNodes.stream()
                .collect(Collectors.toMap(Node::getId, Node::getName));
        Map<UUID, String> fromNodeNameMap = fromNodes.stream()
                .collect(Collectors.toMap(Node::getId, Node::getName));

        List<EdgeDiff> edgeDiffs = new ArrayList<>();
        toEdgeMap.forEach((key, edge) -> {
            String src = toNodeNameMap.getOrDefault(edge.getSourceNodeId(), "");
            String tgt = toNodeNameMap.getOrDefault(edge.getTargetNodeId(), "");
            edgeDiffs.add(new EdgeDiff(edge, fromEdgeMap.containsKey(key) ? "unchanged" : "added", src, tgt));
        });
        fromEdgeMap.forEach((key, edge) -> {
            if (!toEdgeMap.containsKey(key)) {
                String src = fromNodeNameMap.getOrDefault(edge.getSourceNodeId(), "");
                String tgt = fromNodeNameMap.getOrDefault(edge.getTargetNodeId(), "");
                edgeDiffs.add(new EdgeDiff(edge, "removed", src, tgt));
            }
        });

        return new DiffResult(nodeDiffs, edgeDiffs);
    }

    private String nodeKey(Node n) {
        return n.getType().name() + "|" + n.getName() + "|" + Objects.toString(n.getFilePath(), "");
    }

    private <T> Map<String, T> index(List<T> list, Function<T, String> keyFn) {
        return list.stream().collect(Collectors.toMap(keyFn, Function.identity(), (a, b) -> a));
    }
}
