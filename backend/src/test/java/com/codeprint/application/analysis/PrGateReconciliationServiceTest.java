// PrGateReconciliationService 단위 테스트 — 시간창 판정·재트리거 분기·프로젝트별 격리 회귀 방지
package com.codeprint.application.analysis;

import com.codeprint.infrastructure.github.GitHubApiClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PrGateReconciliationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-17T12:00:00Z");

    private AnalysisFacade analysisFacade;
    private GitHubApiClient gitHubApiClient;
    private PrReviewRunner prReviewRunner;
    private PrGateReconciliationService service;

    private void setUp() {
        analysisFacade = mock(AnalysisFacade.class);
        gitHubApiClient = mock(GitHubApiClient.class);
        prReviewRunner = mock(PrReviewRunner.class);
        service = new PrGateReconciliationService(analysisFacade, gitHubApiClient, prReviewRunner);
    }

    @Test
    @DisplayName("withinReconcileWindow — 유예시간(10분) 이내면 대상 아님")
    void withinReconcileWindow_tooRecent_false() {
        assertThat(PrGateReconciliationService.withinReconcileWindow(
                NOW.minusSeconds(5 * 60), NOW)).isFalse();
    }

    @Test
    @DisplayName("withinReconcileWindow — 상한(24시간) 초과면 대상 아님")
    void withinReconcileWindow_tooOld_false() {
        assertThat(PrGateReconciliationService.withinReconcileWindow(
                NOW.minusSeconds(25 * 3600), NOW)).isFalse();
    }

    @Test
    @DisplayName("withinReconcileWindow — 유예시간과 상한 사이면 대상")
    void withinReconcileWindow_inRange_true() {
        assertThat(PrGateReconciliationService.withinReconcileWindow(
                NOW.minusSeconds(3600), NOW)).isTrue();
    }

    @Test
    @DisplayName("codeprint/structure 상태가 없는 시간창 내 PR만 재트리거한다")
    void reconcile_missingStatus_triggersReview() {
        setUp();
        UUID projectId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        var project = new PrGateConnectedProject(projectId, ownerId, "https://github.com/o/r", "tok");
        when(analysisFacade.listPrGateConnectedProjects()).thenReturn(List.of(project));
        when(gitHubApiClient.fetchOpenPullRequests("https://github.com/o/r", "tok")).thenReturn(List.of(
                new GitHubApiClient.OpenPullRequest(7, "sha7", Instant.now().minusSeconds(3600))));
        when(gitHubApiClient.hasStructureCommitStatus("https://github.com/o/r", "sha7", "tok")).thenReturn(false);

        int triggered = service.reconcile();

        assertThat(triggered).isEqualTo(1);
        verify(prReviewRunner).reviewAsync(projectId, 7, ownerId, "tok");
    }

    @Test
    @DisplayName("codeprint/structure 상태가 이미 있으면 재트리거하지 않는다")
    void reconcile_hasStatus_skipped() {
        setUp();
        var project = new PrGateConnectedProject(UUID.randomUUID(), UUID.randomUUID(), "https://github.com/o/r", "tok");
        when(analysisFacade.listPrGateConnectedProjects()).thenReturn(List.of(project));
        when(gitHubApiClient.fetchOpenPullRequests(anyString(), anyString())).thenReturn(List.of(
                new GitHubApiClient.OpenPullRequest(1, "sha1", Instant.now().minusSeconds(3600))));
        when(gitHubApiClient.hasStructureCommitStatus(anyString(), anyString(), anyString())).thenReturn(true);

        int triggered = service.reconcile();

        assertThat(triggered).isEqualTo(0);
        verify(prReviewRunner, never()).reviewAsync(any(), anyInt(), any(), anyString());
    }

    @Test
    @DisplayName("시간창 밖 PR(유예시간 이내)은 상태 조회조차 하지 않는다")
    void reconcile_tooRecentPr_skippedWithoutStatusCheck() {
        setUp();
        var project = new PrGateConnectedProject(UUID.randomUUID(), UUID.randomUUID(), "https://github.com/o/r", "tok");
        when(analysisFacade.listPrGateConnectedProjects()).thenReturn(List.of(project));
        // reconcile()이 내부적으로 실제 Instant.now()를 쓰므로, 테스트 실행 시점 기준 60초 전으로 맞춰
        // 유예시간(10분) 이내에 확실히 들어오게 한다.
        when(gitHubApiClient.fetchOpenPullRequests(anyString(), anyString())).thenReturn(List.of(
                new GitHubApiClient.OpenPullRequest(1, "sha1", Instant.now().minusSeconds(60))));

        int triggered = service.reconcile();

        assertThat(triggered).isEqualTo(0);
        verify(gitHubApiClient, never()).hasStructureCommitStatus(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("한 프로젝트 GitHub API 실패가 다른 프로젝트 처리를 막지 않는다")
    void reconcile_oneProjectFails_othersStillProcessed() {
        setUp();
        var failing = new PrGateConnectedProject(UUID.randomUUID(), UUID.randomUUID(), "https://github.com/a/fail", "tok1");
        UUID okProjectId = UUID.randomUUID();
        UUID okOwnerId = UUID.randomUUID();
        var ok = new PrGateConnectedProject(okProjectId, okOwnerId, "https://github.com/b/ok", "tok2");
        when(analysisFacade.listPrGateConnectedProjects()).thenReturn(List.of(failing, ok));
        when(gitHubApiClient.fetchOpenPullRequests("https://github.com/a/fail", "tok1"))
                .thenThrow(new RuntimeException("GitHub API 500"));
        when(gitHubApiClient.fetchOpenPullRequests("https://github.com/b/ok", "tok2")).thenReturn(List.of(
                new GitHubApiClient.OpenPullRequest(3, "sha3", Instant.now().minusSeconds(3600))));
        when(gitHubApiClient.hasStructureCommitStatus("https://github.com/b/ok", "sha3", "tok2")).thenReturn(false);

        int triggered = service.reconcile();

        assertThat(triggered).isEqualTo(1);
        verify(prReviewRunner).reviewAsync(okProjectId, 3, okOwnerId, "tok2");
    }
}
