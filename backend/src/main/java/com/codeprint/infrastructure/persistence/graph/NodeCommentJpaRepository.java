// NodeCommentRepository JPA 구현체
package com.codeprint.infrastructure.persistence.graph;

import com.codeprint.domain.graph.NodeComment;
import com.codeprint.domain.graph.NodeCommentRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NodeCommentJpaRepository extends JpaRepository<NodeComment, UUID>, NodeCommentRepository {

    // 그래프+노드 기준 코멘트 목록 — 생성 시간 오름차순
    List<NodeComment> findByGraphIdAndNodeIdOrderByCreatedAtAsc(UUID graphId, UUID nodeId);

    @Override
    default List<NodeComment> findByGraphIdAndNodeId(UUID graphId, UUID nodeId) {
        return findByGraphIdAndNodeIdOrderByCreatedAtAsc(graphId, nodeId);
    }
}
