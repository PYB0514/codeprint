// 벤치 파이프라인에서 GraphBuilder를 DB 없이 구동하기 위한 더미 — 공유 게시물 개념이 없어 항상 빈 집합
package com.codeprint.bench;

import com.codeprint.domain.graph.port.SnapshotReferencePort;

import java.util.Set;
import java.util.UUID;

final class NoOpSnapshotReferencePort implements SnapshotReferencePort {
    @Override public Set<UUID> findReferencedGraphIds(UUID projectId) { return Set.of(); }
}
