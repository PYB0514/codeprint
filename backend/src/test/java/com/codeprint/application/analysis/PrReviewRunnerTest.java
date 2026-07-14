// PrReviewRunner 비동기 실패 처리 회귀 테스트 — GATE_GAPS.md [G-4] 재발방지(체크가 조용히 사라지지 않고 error로 명시)
package com.codeprint.application.analysis;

import com.codeprint.infrastructure.github.GitHubApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PrReviewRunnerTest {

    @Mock private PrReviewService prReviewService;
    @Mock private AnalysisFacade analysisFacade;
    @Mock private GitHubApiClient gitHubApiClient;

    private PrReviewRunner runner;

    private final UUID projectId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final int prNumber = 42;
    private final String token = "tok";

    @BeforeEach
    void setUp() {
        runner = new PrReviewRunner(prReviewService, analysisFacade, gitHubApiClient);
    }

    @Test
    @DisplayName("리뷰가 정상 완료되면 실패 상태를 게시하지 않는다")
    void doesNotPostFailureStatusOnSuccess() {
        when(prReviewService.review(projectId, prNumber, ownerId, token)).thenReturn(Map.of());

        runner.reviewAsync(projectId, prNumber, ownerId, token);

        verifyNoInteractions(analysisFacade);
        verify(gitHubApiClient, never()).createCommitStatus(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("리뷰 실행 중 예외가 나면 PR head 커밋에 error 상태를 게시한다")
    void postsErrorStatusWhenReviewThrows() {
        when(prReviewService.review(projectId, prNumber, ownerId, token)).thenThrow(new RuntimeException("boom"));
        when(analysisFacade.resolveOwnedRepoUrl(projectId, ownerId)).thenReturn("https://github.com/a/b");
        when(gitHubApiClient.fetchPullRequestHeadSha("https://github.com/a/b", prNumber, token)).thenReturn("sha123");

        runner.reviewAsync(projectId, prNumber, ownerId, token);

        verify(gitHubApiClient).createCommitStatus(eq("https://github.com/a/b"), eq("sha123"), eq("error"), any(), eq(null), eq(token));
    }

    @Test
    @DisplayName("실패 상태 게시 자체가 실패해도(예: repoUrl 조회 실패) 예외를 밖으로 던지지 않는다")
    void swallowsExceptionFromFailureStatusPostingItself() {
        when(prReviewService.review(projectId, prNumber, ownerId, token)).thenThrow(new RuntimeException("boom"));
        when(analysisFacade.resolveOwnedRepoUrl(projectId, ownerId)).thenThrow(new RuntimeException("repo lookup failed"));

        runner.reviewAsync(projectId, prNumber, ownerId, token);
    }
}
