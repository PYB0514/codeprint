// 벤치 러너용 인메모리 GraphRepository — DB 없이 GraphBuilder를 구동(LocalAnalyzer의 동일 패턴 재사용)
package com.codeprint.bench;

import com.codeprint.domain.graph.Edge;
import com.codeprint.domain.graph.Graph;
import com.codeprint.domain.graph.GraphRepository;
import com.codeprint.domain.graph.Node;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class InMemoryGraphRepository implements GraphRepository {
    private final List<Graph> graphs = new ArrayList<>();
    private final List<Node> nodes = new ArrayList<>();
    private final List<Edge> edges = new ArrayList<>();

    @Override
    public Graph save(Graph graph) {
        graphs.add(graph);
        return graph;
    }

    @Override
    public Optional<Graph> findById(UUID id) {
        return graphs.stream().filter(g -> g.getId().equals(id)).findFirst();
    }

    @Override
    public List<Graph> findByProjectId(UUID projectId) {
        return graphs.stream().filter(g -> projectId.equals(g.getProjectId())).toList();
    }

    @Override
    public List<Node> findNodesByGraphId(UUID graphId) {
        return nodes.stream().filter(n -> graphId.equals(n.getGraphId())).toList();
    }

    @Override
    public List<Edge> findEdgesByGraphId(UUID graphId) {
        return edges.stream().filter(e -> graphId.equals(e.getGraphId())).toList();
    }

    @Override
    public Node saveNode(Node node) {
        nodes.add(node);
        return node;
    }

    @Override
    public Optional<Node> findNodeById(UUID nodeId) {
        return nodes.stream().filter(n -> n.getId().equals(nodeId)).findFirst();
    }

    @Override
    public Edge saveEdge(Edge edge) {
        edges.add(edge);
        return edge;
    }

    @Override
    public void deleteById(UUID id) {
        graphs.removeIf(g -> g.getId().equals(id));
        nodes.removeIf(n -> id.equals(n.getGraphId()));
        edges.removeIf(e -> id.equals(e.getGraphId()));
    }

    @Override
    public Optional<Graph> findTopByProjectIdOrderByCreatedAtDesc(UUID projectId) {
        return findByProjectId(projectId).stream()
                .max((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()));
    }

    @Override
    public void clearPinnedSlot(UUID projectId, int slot) {
    }
}
