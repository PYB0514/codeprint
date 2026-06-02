// 그래프 도메인 Repository JPA 구현체 (노드/엣지 포함)
package com.codeprint.infrastructure.persistence.graph;

import com.codeprint.domain.graph.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class GraphRepositoryImpl implements GraphRepository {

    private final GraphJpaRepository graphJpa;
    private final NodeJpaRepository nodeJpa;
    private final EdgeJpaRepository edgeJpa;

    @Override
    public Graph save(Graph graph) {
        return graphJpa.save(graph);
    }

    @Override
    public Optional<Graph> findById(UUID id) {
        return graphJpa.findById(id);
    }

    @Override
    public List<Graph> findByProjectId(UUID projectId) {
        return graphJpa.findByProjectId(projectId);
    }

    @Override
    public List<Node> findNodesByGraphId(UUID graphId) {
        return nodeJpa.findByGraphId(graphId);
    }

    @Override
    public List<Edge> findEdgesByGraphId(UUID graphId) {
        return edgeJpa.findByGraphId(graphId);
    }

    @Override
    public Node saveNode(Node node) {
        return nodeJpa.save(node);
    }

    @Override
    public Edge saveEdge(Edge edge) {
        return edgeJpa.save(edge);
    }

    @Override
    public void deleteById(UUID id) {
        graphJpa.deleteById(id);
    }
}
