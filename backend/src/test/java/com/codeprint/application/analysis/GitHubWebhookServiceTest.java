// GitHubWebhookService 단위 테스트 — 이벤트/액션 필터·repo 역해석·비동기 트리거 분기 회귀 방지 (서명 검증 자체는 PrWebhookTargetAdapter 책임)
package com.codeprint.application.analysis;

import com.codeprint.domain.analysis.port.PrWebhookTargetPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GitHubWebhookServiceTest {

    private static final String SIG = "sha256=irrelevant-for-this-layer";

    private PrWebhookTargetPort targetPort;
    private PrReviewRunner reviewRunner;
    private GitHubWebhookService service;

    @BeforeEach
    void setUp() {
        targetPort = mock(PrWebhookTargetPort.class);
        reviewRunner = mock(PrReviewRunner.class);
        service = new GitHubWebhookService(targetPort, reviewRunner);
    }

    // pull_request payload JSON 바이트 생성
    private byte[] prPayload(String action, String fullName, int number) {
        String json = "{\"action\":\"" + action + "\",\"number\":" + number
                + ",\"repository\":{\"full_name\":\"" + fullName + "\"}}";
        return json.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("pull_request 이외 이벤트는 IGNORED — repo 역해석 자체를 시도하지 않는다")
    void handle_nonPullRequestEvent_ignored() {
        byte[] body = prPayload("opened", "owner/repo", 1);

        GitHubWebhookService.Result result = service.handle("push", SIG, body);

        assertThat(result).isEqualTo(GitHubWebhookService.Result.IGNORED);
        verifyNoInteractions(targetPort);
        verifyNoInteractions(reviewRunner);
    }

    @Test
    @DisplayName("payload 파싱 실패(잘못된 JSON)는 IGNORED")
    void handle_malformedJson_ignored() {
        byte[] body = "{ this is not json".getBytes(StandardCharsets.UTF_8);

        GitHubWebhookService.Result result = service.handle("pull_request", SIG, body);

        assertThat(result).isEqualTo(GitHubWebhookService.Result.IGNORED);
        verifyNoInteractions(reviewRunner);
    }

    @Test
    @DisplayName("리뷰 대상이 아닌 액션(closed)은 IGNORED")
    void handle_nonReviewableAction_ignored() {
        byte[] body = prPayload("closed", "owner/repo", 1);

        GitHubWebhookService.Result result = service.handle("pull_request", SIG, body);

        assertThat(result).isEqualTo(GitHubWebhookService.Result.IGNORED);
        verifyNoInteractions(reviewRunner);
    }

    @Test
    @DisplayName("repository.full_name이 없으면 IGNORED")
    void handle_missingRepository_ignored() {
        byte[] body = "{\"action\":\"opened\",\"number\":1}".getBytes(StandardCharsets.UTF_8);

        GitHubWebhookService.Result result = service.handle("pull_request", SIG, body);

        assertThat(result).isEqualTo(GitHubWebhookService.Result.IGNORED);
        verifyNoInteractions(reviewRunner);
    }

    @Test
    @DisplayName("number가 0 이하면 IGNORED")
    void handle_invalidNumber_ignored() {
        byte[] body = prPayload("opened", "owner/repo", 0);

        GitHubWebhookService.Result result = service.handle("pull_request", SIG, body);

        assertThat(result).isEqualTo(GitHubWebhookService.Result.IGNORED);
        verifyNoInteractions(reviewRunner);
    }

    @Test
    @DisplayName("서명 불일치 또는 연결된 프로젝트 없음(포트가 빈 값 반환)이면 UNAUTHORIZED — 리뷰 미트리거")
    void handle_noTarget_unauthorized() {
        byte[] body = prPayload("opened", "owner/repo", 5);
        when(targetPort.resolve(eq("owner/repo"), any(), anyString())).thenReturn(Optional.empty());

        GitHubWebhookService.Result result = service.handle("pull_request", SIG, body);

        assertThat(result).isEqualTo(GitHubWebhookService.Result.UNAUTHORIZED);
        verify(reviewRunner, never()).reviewAsync(any(), anyInt(), any(), anyString());
    }

    @Test
    @DisplayName("유효한 opened PR은 ACCEPTED — 해석된 대상으로 리뷰를 비동기 트리거한다")
    void handle_validOpenedPr_accepted_triggersReview() {
        UUID projectId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        byte[] body = prPayload("opened", "owner/repo", 7);
        when(targetPort.resolve(eq("owner/repo"), any(), anyString()))
                .thenReturn(Optional.of(new PrWebhookTargetPort.Target(projectId, ownerId, "gh-token")));

        GitHubWebhookService.Result result = service.handle("pull_request", SIG, body);

        assertThat(result).isEqualTo(GitHubWebhookService.Result.ACCEPTED);
        verify(reviewRunner).reviewAsync(projectId, 7, ownerId, "gh-token");
    }

    @Test
    @DisplayName("synchronize(커밋 push) 액션도 리뷰 대상이라 ACCEPTED")
    void handle_synchronizeAction_accepted() {
        UUID projectId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        byte[] body = prPayload("synchronize", "owner/repo", 9);
        when(targetPort.resolve(eq("owner/repo"), any(), anyString()))
                .thenReturn(Optional.of(new PrWebhookTargetPort.Target(projectId, ownerId, "tok")));

        GitHubWebhookService.Result result = service.handle("pull_request", SIG, body);

        assertThat(result).isEqualTo(GitHubWebhookService.Result.ACCEPTED);
        verify(reviewRunner).reviewAsync(projectId, 9, ownerId, "tok");
    }
}
