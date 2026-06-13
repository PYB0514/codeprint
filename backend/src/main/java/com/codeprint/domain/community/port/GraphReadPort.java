// Community 도메인에서 graph 컨텍스트의 그래프 데이터를 조회하는 포트 (graph 도메인 모델 비노출)
package com.codeprint.domain.community.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GraphReadPort {

    // graphId로 그래프 노드·엣지 스냅샷 조회 — community 소유 view로 반환
    Optional<GraphSnapshot> findGraphSnapshot(UUID graphId);

    // graphId가 속한 프로젝트 ID 조회
    Optional<UUID> findProjectId(UUID graphId);

    // 그래프 노드·엣지 스냅샷 (community 소유 published language)
    record GraphSnapshot(UUID graphId, List<NodeView> nodes, List<EdgeView> edges) {}

    // 그래프 노드 view — graph 도메인 Node에서 community가 필요로 하는 필드만 추림
    record NodeView(UUID id, String type, String name, String filePath, String language,
                    double posX, double posY, Object comment, boolean hidden) {}

    // 그래프 엣지 view — graph 도메인 Edge에서 community가 필요로 하는 필드만 추림
    record EdgeView(UUID id, String type, UUID source, UUID target, String edgeIdentifier, boolean hidden) {}
}
