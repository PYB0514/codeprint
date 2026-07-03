// Community 도메인에서 graph 컨텍스트의 그래프 데이터를 조회하는 포트 (graph 도메인 모델 비노출)
package com.codeprint.domain.community.port;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface GraphReadPort {

    // graphId로 그래프 노드·엣지 스냅샷 조회 — community 소유 view로 반환
    Optional<GraphSnapshot> findGraphSnapshot(UUID graphId);

    // graphId가 속한 프로젝트 ID 조회
    Optional<UUID> findProjectId(UUID graphId);

    // 프로젝트의 최신 그래프에서 지정 슬롯의 프리셋 설정을 조회(저장 안 됐으면 기본값) — 게시글 스냅샷 캡처 전용
    Optional<PresetSnapshot> findLatestPresetConfig(UUID projectId, UUID userId, int presetSlot);

    // 그래프 노드·엣지 스냅샷 (community 소유 published language)
    record GraphSnapshot(UUID graphId, List<NodeView> nodes, List<EdgeView> edges) {}

    // 캡처된 프리셋 스냅샷 — graphId(불변)와 그 순간의 config 사본
    record PresetSnapshot(UUID graphId, Map<String, Object> config) {}

    // 그래프 노드 view — graph 도메인 Node에서 community가 필요로 하는 필드만 추림
    record NodeView(UUID id, String type, String name, String filePath, String language,
                    double posX, double posY, Object comment, boolean hidden) {}

    // 그래프 엣지 view — graph 도메인 Edge에서 community가 필요로 하는 필드만 추림
    record EdgeView(UUID id, String type, UUID source, UUID target, String edgeIdentifier, boolean hidden) {}
}
