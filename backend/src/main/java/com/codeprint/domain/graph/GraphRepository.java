package com.codeprint.domain.graph;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GraphRepository {

    Graph save(Graph graph);

    Optional<Graph> findById(UUID id);

    List<Graph> findByProjectId(UUID projectId);

    List<Node> findNodesByGraphId(UUID graphId);

    List<Edge> findEdgesByGraphId(UUID graphId);

    Node saveNode(Node node);

    Edge saveEdge(Edge edge);

    void deleteById(UUID id);
}
