// 그래프 도메인 Repository 인터페이스 (노드/엣지 포함)
package com.codeprint.domain.graph;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GraphRepository {

    // 그래프 저장
    Graph save(Graph graph);

    // ID로 그래프 조회
    Optional<Graph> findById(UUID id);

    // 프로젝트 ID로 그래프 목록 조회
    List<Graph> findByProjectId(UUID projectId);

    // 그래프 ID로 노드 목록 조회
    List<Node> findNodesByGraphId(UUID graphId);

    // 그래프 ID로 엣지 목록 조회
    List<Edge> findEdgesByGraphId(UUID graphId);

    // 노드 저장
    Node saveNode(Node node);

    // 노드 ID로 노드 조회
    Optional<Node> findNodeById(UUID nodeId);

    // 엣지 저장
    Edge saveEdge(Edge edge);

    // 그래프 삭제
    void deleteById(UUID id);

    // 프로젝트의 최신 그래프 조회
    Optional<Graph> findTopByProjectIdOrderByCreatedAtDesc(UUID projectId);

    // 같은 프로젝트의 지정 고정 슬롯을 비움 (고정 덮어쓰기용)
    void clearPinnedSlot(UUID projectId, int slot);
}
