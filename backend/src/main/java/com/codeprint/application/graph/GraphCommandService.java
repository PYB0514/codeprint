// 그래프/노드/엣지 생성 애플리케이션 서비스
package com.codeprint.application.graph;

import com.codeprint.domain.graph.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class GraphCommandService {

    private final GraphRepository graphRepository;

    public Graph createGraph(UUID projectId, UUID analysisId) {
        Graph graph = Graph.create(projectId, analysisId);
        return graphRepository.save(graph);
    }

    public Node addNode(UUID graphId, NodeType type, String name, String filePath, String language) {
        graphRepository.findById(graphId)
                .orElseThrow(() -> new IllegalArgumentException("Graph not found: " + graphId));
        Node node = Node.create(graphId, type, name, filePath, language);
        return graphRepository.saveNode(node);
    }

    public Edge addEdge(UUID graphId, String edgeIdentifier, EdgeType type,
                        UUID sourceNodeId, UUID targetNodeId) {
        graphRepository.findById(graphId)
                .orElseThrow(() -> new IllegalArgumentException("Graph not found: " + graphId));
        Edge edge = Edge.create(graphId, edgeIdentifier, type, sourceNodeId, targetNodeId);
        return graphRepository.saveEdge(edge);
    }

    @Transactional(readOnly = true)
    public List<Node> getNodes(UUID graphId) {
        return graphRepository.findNodesByGraphId(graphId);
    }

    @Transactional(readOnly = true)
    public List<Edge> getEdges(UUID graphId) {
        return graphRepository.findEdgesByGraphId(graphId);
    }
}
