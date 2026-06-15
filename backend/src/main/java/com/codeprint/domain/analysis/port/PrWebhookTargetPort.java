// webhook의 repo 식별자를 PR 리뷰 대상(프로젝트·소유자·토큰)으로 역해석하는 포트 (analysis 도메인 소유)
package com.codeprint.domain.analysis.port;

import java.util.Optional;
import java.util.UUID;

public interface PrWebhookTargetPort {

    // owner/repo 형태 식별자로 리뷰 대상을 해석 — 매칭 프로젝트가 없으면 빈 값
    Optional<Target> resolve(String ownerRepo);

    // PR 리뷰 실행에 필요한 대상 정보 — 프로젝트, 소유자, 소유자의 GitHub 토큰
    record Target(UUID projectId, UUID ownerId, String githubToken) {}
}
