// 그래프 뷰 프리셋 도메인 Repository 인터페이스
package com.codeprint.domain.graph;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GraphViewPresetRepository {

    List<GraphViewPreset> findByGraphIdAndUserIdOrderBySlotAsc(UUID graphId, UUID userId);

    Optional<GraphViewPreset> findByGraphIdAndUserIdAndSlot(UUID graphId, UUID userId, int slot);

    GraphViewPreset save(GraphViewPreset preset);

    void delete(GraphViewPreset preset);
}
