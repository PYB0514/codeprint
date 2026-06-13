// Community 도메인에서 project 컨텍스트의 공개 레포 URL을 조회하는 포트
package com.codeprint.domain.community.port;

import java.util.Optional;
import java.util.UUID;

public interface ProjectReadPort {

    // 요청 사용자가 소유한 공개 프로젝트의 레포 URL 조회 — 비공개·미소유·부재 시 empty
    Optional<String> findPublicRepoUrl(UUID projectId, UUID userId);
}
