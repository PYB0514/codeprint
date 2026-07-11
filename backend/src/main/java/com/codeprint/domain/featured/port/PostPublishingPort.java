// Featured 도메인에서 community 컨텍스트에 통합 게시글을 발행하는 포트
package com.codeprint.domain.featured.port;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface PostPublishingPort {

    // 다중 스냅샷 전용 게시글 생성 — 시스템 계정 소유, 대표 그래프 없이 스냅샷으로만 구성
    UUID createPost(String title, String content);

    // 프로젝트의 최신 그래프 설정을 스냅샷용으로 캡처 — 그래프가 아직 없으면(분석 미완료) empty
    Optional<SnapshotToPublish> captureSnapshot(UUID projectId);

    // 게시글의 그래프 스냅샷을 전부 교체(기존 삭제 후 재저장) — 매일 갱신용
    void replaceSnapshots(UUID postId, List<SnapshotToPublish> snapshots);

    // 게시글에 저장된 스냅샷의 프로젝트ID → position 매핑 — 랜딩페이지 카드가 실제 노출 순번으로 딥링크하기 위함
    Map<UUID, Integer> getSnapshotPositions(UUID postId);

    // 게시글에 실을 스냅샷 정보 — 프로젝트ID·그래프ID·캡처된 config
    record SnapshotToPublish(UUID projectId, UUID graphId, Map<String, Object> config) {}
}
