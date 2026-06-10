// 노드 스타일 도메인 Repository 인터페이스
package com.codeprint.domain.graph;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NodeStyleRepository {

    // 그래프의 모든 노드 스타일 조회
    List<NodeStyle> findByGraphId(UUID graphId);

    // 특정 노드 스타일 조회
    Optional<NodeStyle> findByGraphIdAndNodeId(UUID graphId, UUID nodeId);

    // 노드 스타일 저장
    NodeStyle save(NodeStyle style);

    // 특정 노드 스타일 삭제
    void deleteByGraphIdAndNodeId(UUID graphId, UUID nodeId);
}
