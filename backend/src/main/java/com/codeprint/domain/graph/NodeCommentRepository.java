// 노드 코멘트 레포지토리 인터페이스
package com.codeprint.domain.graph;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NodeCommentRepository {

    // 코멘트 저장
    NodeComment save(NodeComment comment);

    // ID로 조회
    Optional<NodeComment> findById(UUID id);

    // 특정 노드의 코멘트 목록 조회 (최신순)
    List<NodeComment> findByGraphIdAndNodeId(UUID graphId, String nodeId);

    // 코멘트 삭제
    void deleteById(UUID id);
}
