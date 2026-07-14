// PR 리뷰를 비동기로 실행하는 빈 — webhook이 GitHub 타임아웃 없이 빠르게 응답하도록 분리
package com.codeprint.application.analysis;

import com.codeprint.infrastructure.github.GitHubApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PrReviewRunner {

    private final PrReviewService prReviewService;
    private final AnalysisFacade analysisFacade;
    private final GitHubApiClient gitHubApiClient;

    // PR 리뷰를 비동기 실행 — webhook 응답엔 영향 없도록 예외를 흡수하되, 체크가 조용히 사라지지 않도록 error 상태를 게시(G-4 재발방지)
    @Async
    public void reviewAsync(UUID projectId, int prNumber, UUID ownerId, String githubToken) {
        try {
            prReviewService.review(projectId, prNumber, ownerId, githubToken);
        } catch (Exception e) {
            log.error("webhook PR 리뷰 비동기 실행 실패: project={}, pr={}", projectId, prNumber, e);
            postFailureStatus(projectId, prNumber, ownerId, githubToken);
        }
    }

    // 리뷰 자체가 실패해 코멘트·게이트 상태가 전혀 게시되지 못한 경우 — required check가 응답 없이 사라지는 대신 error로 명시
    private void postFailureStatus(UUID projectId, int prNumber, UUID ownerId, String githubToken) {
        try {
            String repoUrl = analysisFacade.resolveOwnedRepoUrl(projectId, ownerId);
            String headSha = gitHubApiClient.fetchPullRequestHeadSha(repoUrl, prNumber, githubToken);
            gitHubApiClient.createCommitStatus(repoUrl, headSha, "error", "구조 검사 실행 중 오류 발생 — 서버 로그 확인 필요", null, githubToken);
        } catch (Exception ex) {
            log.error("PR 리뷰 실패 상태 게시도 실패: project={}, pr={}", projectId, prNumber, ex);
        }
    }
}
