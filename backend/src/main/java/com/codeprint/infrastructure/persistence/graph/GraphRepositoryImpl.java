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

    // 그래프 엔티티를 저장하고 반환
    @Override
    public Graph save(Graph graph) {
        return graphJpa.save(graph);
    }

    // UUID로 그래프 조회
    @Override
    public Optional<Graph> findById(UUID id) {
        return graphJpa.findById(id);
    }

    // 프로젝트 ID로 그래프 목록 조회
    @Override
    public List<Graph> findByProjectId(UUID projectId) {
        return graphJpa.findByProjectId(projectId);
    }

    // 그래프 ID로 노드 목록 조회
    @Override
    public List<Node> findNodesByGraphId(UUID graphId) {
        return nodeJpa.findByGraphId(graphId);
    }

    // 그래프 ID로 엣지 목록 조회
    @Override
    public List<Edge> findEdgesByGraphId(UUID graphId) {
        return edgeJpa.findByGraphId(graphId);
    }

    // 노드 엔티티를 저장하고 반환
    @Override
    public Node saveNode(Node node) {
        return nodeJpa.save(node);
    }

    // UUID로 노드 단건 조회
    @Override
    public Optional<Node> findNodeById(UUID nodeId) {
        return nodeJpa.findById(nodeId);
    }

    // 엣지 엔티티를 저장하고 반환
    @Override
    public Edge saveEdge(Edge edge) {
        return edgeJpa.save(edge);
    }

    // UUID로 그래프 삭제
    @Override
    public void deleteById(UUID id) {
        graphJpa.deleteById(id);
    }

    // 프로젝트의 최신 그래프 조회
    @Override
    public Optional<Graph> findTopByProjectIdOrderByCreatedAtDesc(UUID projectId) {
        return graphJpa.findTopByProjectIdOrderByCreatedAtDesc(projectId);
    }

    // 같은 프로젝트의 지정 고정 슬롯을 비움 (고정 덮어쓰기용)
    @Override
    public void clearPinnedSlot(UUID projectId, int slot) {
        graphJpa.clearPinnedSlot(projectId, slot);
    }
}
