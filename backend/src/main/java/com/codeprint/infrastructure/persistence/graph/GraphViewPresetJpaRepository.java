// 그래프 뷰 프리셋 JPA 레포지토리
package com.codeprint.infrastructure.persistence.graph;

import com.codeprint.domain.graph.GraphViewPreset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GraphViewPresetJpaRepository extends JpaRepository<GraphViewPreset, UUID> {

    // 특정 그래프의 사용자 프리셋 전체 조회
    List<GraphViewPreset> findByGraphIdAndUserIdOrderBySlotAsc(UUID graphId, UUID userId);

    // 특정 슬롯 조회
    Optional<GraphViewPreset> findByGraphIdAndUserIdAndSlot(UUID graphId, UUID userId, int slot);

    // 특정 그래프의 모든 사용자 프리셋 조회 (공유용 — userId 무관)
    List<GraphViewPreset> findByGraphIdAndSlot(UUID graphId, int slot);
}
