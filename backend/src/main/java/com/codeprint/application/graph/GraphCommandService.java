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

    // 새 그래프를 생성하여 저장
    public Graph createGraph(UUID projectId, UUID analysisId) {
        Graph graph = Graph.create(projectId, analysisId);
        return graphRepository.save(graph);
    }

    // 그래프에 노드를 추가하여 저장
    public Node addNode(UUID graphId, NodeType type, String name, String filePath, String language) {
        graphRepository.findById(graphId)
                .orElseThrow(() -> new IllegalArgumentException("Graph not found: " + graphId));
        Node node = Node.create(graphId, type, name, filePath, language);
        return graphRepository.saveNode(node);
    }

    // 그래프에 엣지를 추가하여 저장
    public Edge addEdge(UUID graphId, String edgeIdentifier, EdgeType type,
                        UUID sourceNodeId, UUID targetNodeId) {
        graphRepository.findById(graphId)
                .orElseThrow(() -> new IllegalArgumentException("Graph not found: " + graphId));
        Edge edge = Edge.create(graphId, edgeIdentifier, type, sourceNodeId, targetNodeId);
        return graphRepository.saveEdge(edge);
    }

    // 그래프 ID로 노드 목록을 조회 (읽기 전용)
    @Transactional(readOnly = true)
    public List<Node> getNodes(UUID graphId) {
        return graphRepository.findNodesByGraphId(graphId);
    }

    // 그래프 ID로 엣지 목록을 조회 (읽기 전용)
    @Transactional(readOnly = true)
    public List<Edge> getEdges(UUID graphId) {
        return graphRepository.findEdgesByGraphId(graphId);
    }

    // 노드 드래그 후 저장된 위치를 업데이트 — 노드가 그래프 소속인지 검증(IDOR 방지)
    public void updateNodePosition(UUID graphId, UUID nodeId, double x, double y) {
        Node node = requireNodeInGraph(graphId, nodeId);
        node.updatePosition(x, y);
        graphRepository.saveNode(node);
    }

    // 노드 사용자 정의 레이블과 메모를 업데이트 — 노드가 그래프 소속인지 검증(IDOR 방지)
    public void updateNodeAnnotation(UUID graphId, UUID nodeId, String userLabel, String userNote) {
        Node node = requireNodeInGraph(graphId, nodeId);
        node.updateAnnotation(userLabel, userNote);
        graphRepository.saveNode(node);
    }

    // 노드 조회 + 그래프 소속 검증 (IDOR 방지) — 자기 그래프 ID + 남의 nodeId 조합 차단
    private Node requireNodeInGraph(UUID graphId, UUID nodeId) {
        Node node = graphRepository.findNodeById(nodeId)
                .orElseThrow(() -> new IllegalArgumentException("Node not found: " + nodeId));
        if (!node.getGraphId().equals(graphId)) {
            throw new IllegalArgumentException("노드가 그래프에 속하지 않습니다: " + nodeId);
        }
        return node;
    }

    // 그래프 버전을 고정 슬롯에 고정 — 같은 슬롯 기존 점유분은 해제(덮어쓰기). 그래프가 프로젝트 소속인지 검증
    public void pinGraph(UUID projectId, UUID graphId, int slot) {
        requireGraphInProject(projectId, graphId);
        graphRepository.clearPinnedSlot(projectId, slot);
        // clearPinnedSlot이 영속성 컨텍스트를 초기화하므로 최신 상태로 재조회 후 고정
        Graph graph = requireGraphInProject(projectId, graphId);
        graph.pin(slot);
        graphRepository.save(graph);
    }

    // 그래프 버전 고정 해제 — 그래프가 프로젝트 소속인지 검증
    public void unpinGraph(UUID projectId, UUID graphId) {
        Graph graph = requireGraphInProject(projectId, graphId);
        graph.unpin();
        graphRepository.save(graph);
    }

    // 그래프 조회 + 프로젝트 소속 검증 (IDOR 방지)
    private Graph requireGraphInProject(UUID projectId, UUID graphId) {
        Graph graph = graphRepository.findById(graphId)
                .orElseThrow(() -> new IllegalArgumentException("Graph not found: " + graphId));
        if (!graph.getProjectId().equals(projectId)) {
            throw new IllegalArgumentException("그래프가 프로젝트에 속하지 않습니다: " + graphId);
        }
        return graph;
    }
}
