// Graph 도메인에서 community 컨텍스트의 공유 게시물 그래프 스냅샷 참조를 조회하는 포트
package com.codeprint.domain.graph.port;

import java.util.Set;
import java.util.UUID;

public interface SnapshotReferencePort {

    // 프로젝트가 가진 그래프 중 게시물 스냅샷이 참조 중인 graph_id 집합 — 보존 정책에서 삭제 대상 제외용
    Set<UUID> findReferencedGraphIds(UUID projectId);
}
