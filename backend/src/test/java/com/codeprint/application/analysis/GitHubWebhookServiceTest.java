// GitHubWebhookService 단위 테스트 — 서명 검증·이벤트/액션 필터·repo 역해석·비동기 트리거 분기 회귀 방지
package com.codeprint.application.analysis;

import com.codeprint.domain.analysis.port.PrWebhookTargetPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;

class GitHubWebhookServiceTest {

    private static final String SECRET = "test-webhook-secret";

    private PrWebhookTargetPort targetPort;
    private PrReviewRunner reviewRunner;
    private GitHubWebhookService service;

    @BeforeEach
    void setUp() {
        targetPort = mock(PrWebhookTargetPort.class);
        reviewRunner = mock(PrReviewRunner.class);
        service = new GitHubWebhookService(targetPort, reviewRunner);
        ReflectionTestUtils.setField(service, "webhookSecret", SECRET);
    }

    // pull_request payload JSON 바이트 생성
    private byte[] prPayload(String action, String fullName, int number) {
        String json = "{\"action\":\"" + action + "\",\"number\":" + number
                + ",\"repository\":{\"full_name\":\"" + fullName + "\"}}";
        return json.getBytes(StandardCharsets.UTF_8);
    }

    // SECRET으로 계산한 유효 X-Hub-Signature-256 헤더 (verify가 기대하는 HMAC-SHA256 16진)
    private String validSig(byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(body);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return "sha256=" + sb;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("서명이 유효하지 않으면 UNAUTHORIZED — 리뷰를 트리거하지 않는다")
    void handle_invalidSignature_unauthorized() {
        byte[] body = prPayload("opened", "owner/repo", 1);

        GitHubWebhookService.Result result = service.handle("pull_request", "sha256=deadbeef", body);

        assertThat(result).isEqualTo(GitHubWebhookService.Result.UNAUTHORIZED);
        verifyNoInteractions(targetPort);
        verifyNoInteractions(reviewRunner);
    }

    @Test
    @DisplayName("pull_request 이외 이벤트는 IGNORED (서명은 유효)")
    void handle_nonPullRequestEvent_ignored() {
        byte[] body = prPayload("opened", "owner/repo", 1);

        GitHubWebhookService.Result result = service.handle("push", validSig(body), body);

        assertThat(result).isEqualTo(GitHubWebhookService.Result.IGNORED);
        verifyNoInteractions(reviewRunner);
    }

    @Test
    @DisplayName("payload 파싱 실패(잘못된 JSON)는 IGNORED")
    void handle_malformedJson_ignored() {
        byte[] body = "{ this is not json".getBytes(StandardCharsets.UTF_8);

        GitHubWebhookService.Result result = service.handle("pull_request", validSig(body), body);

        assertThat(result).isEqualTo(GitHubWebhookService.Result.IGNORED);
        verifyNoInteractions(reviewRunner);
    }

    @Test
    @DisplayName("리뷰 대상이 아닌 액션(closed)은 IGNORED")
    void handle_nonReviewableAction_ignored() {
        byte[] body = prPayload("closed", "owner/repo", 1);

        GitHubWebhookService.Result result = service.handle("pull_request", validSig(body), body);

        assertThat(result).isEqualTo(GitHubWebhookService.Result.IGNORED);
        verifyNoInteractions(reviewRunner);
    }

    @Test
    @DisplayName("repository.full_name이 없으면 IGNORED")
    void handle_missingRepository_ignored() {
        byte[] body = "{\"action\":\"opened\",\"number\":1}".getBytes(StandardCharsets.UTF_8);

        GitHubWebhookService.Result result = service.handle("pull_request", validSig(body), body);

        assertThat(result).isEqualTo(GitHubWebhookService.Result.IGNORED);
        verifyNoInteractions(reviewRunner);
    }

    @Test
    @DisplayName("number가 0 이하면 IGNORED")
    void handle_invalidNumber_ignored() {
        byte[] body = prPayload("opened", "owner/repo", 0);

        GitHubWebhookService.Result result = service.handle("pull_request", validSig(body), body);

        assertThat(result).isEqualTo(GitHubWebhookService.Result.IGNORED);
        verifyNoInteractions(reviewRunner);
    }

    @Test
    @DisplayName("매칭되는 리뷰 대상 프로젝트가 없으면 IGNORED — 리뷰 미트리거")
    void handle_noTarget_ignored() {
        byte[] body = prPayload("opened", "owner/repo", 5);
        when(targetPort.resolve("owner/repo")).thenReturn(Optional.empty());

        GitHubWebhookService.Result result = service.handle("pull_request", validSig(body), body);

        assertThat(result).isEqualTo(GitHubWebhookService.Result.IGNORED);
        verify(reviewRunner, never()).reviewAsync(any(), anyInt(), any(), anyString());
    }

    @Test
    @DisplayName("유효한 opened PR은 ACCEPTED — 해석된 대상으로 리뷰를 비동기 트리거한다")
    void handle_validOpenedPr_accepted_triggersReview() {
        UUID projectId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        byte[] body = prPayload("opened", "owner/repo", 7);
        when(targetPort.resolve("owner/repo"))
                .thenReturn(Optional.of(new PrWebhookTargetPort.Target(projectId, ownerId, "gh-token")));

        GitHubWebhookService.Result result = service.handle("pull_request", validSig(body), body);

        assertThat(result).isEqualTo(GitHubWebhookService.Result.ACCEPTED);
        verify(reviewRunner).reviewAsync(projectId, 7, ownerId, "gh-token");
    }

    @Test
    @DisplayName("synchronize(커밋 push) 액션도 리뷰 대상이라 ACCEPTED")
    void handle_synchronizeAction_accepted() {
        UUID projectId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        byte[] body = prPayload("synchronize", "owner/repo", 9);
        when(targetPort.resolve("owner/repo"))
                .thenReturn(Optional.of(new PrWebhookTargetPort.Target(projectId, ownerId, "tok")));

        GitHubWebhookService.Result result = service.handle("pull_request", validSig(body), body);

        assertThat(result).isEqualTo(GitHubWebhookService.Result.ACCEPTED);
        verify(reviewRunner).reviewAsync(projectId, 9, ownerId, "tok");
    }
}
