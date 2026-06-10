// 그래프 뷰 프리셋 도메인 Repository 인터페이스
package com.codeprint.domain.graph;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GraphViewPresetRepository {

    // 사용자 슬롯 목록 오름차순 조회
    List<GraphViewPreset> findByGraphIdAndUserIdOrderBySlotAsc(UUID graphId, UUID userId);

    // 특정 슬롯 프리셋 조회
    Optional<GraphViewPreset> findByGraphIdAndUserIdAndSlot(UUID graphId, UUID userId, int slot);

    // 프리셋 저장
    GraphViewPreset save(GraphViewPreset preset);

    // 프리셋 삭제
    void delete(GraphViewPreset preset);
}
