// GitHub pull_request webhook을 검증·파싱해 PR 리뷰를 비동기 트리거하는 서비스 (Tier 1 자동화)
package com.codeprint.application.analysis;

import com.codeprint.domain.analysis.WebhookSignatureVerifier;
import com.codeprint.domain.analysis.port.PrWebhookTargetPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubWebhookService {

    // 리뷰를 트리거하는 PR 액션 — 열림/푸시/재오픈/리뷰준비
    private static final Set<String> REVIEWABLE_ACTIONS =
            Set.of("opened", "synchronize", "reopened", "ready_for_review");

    private final PrWebhookTargetPort prWebhookTargetPort;
    private final PrReviewRunner prReviewRunner;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${github.webhook-secret:}")
    private String webhookSecret;

    // webhook 처리 결과 — 컨트롤러가 HTTP 상태로 매핑
    public enum Result { UNAUTHORIZED, IGNORED, ACCEPTED }

    // 서명 검증 → 이벤트/액션 필터 → repo 역해석 → PR 리뷰 비동기 트리거
    public Result handle(String eventType, String signatureHeader, byte[] rawBody) {
        if (!WebhookSignatureVerifier.verify(webhookSecret, rawBody, signatureHeader)) {
            log.warn("webhook 서명 검증 실패 (event={})", eventType);
            return Result.UNAUTHORIZED;
        }
        if (!"pull_request".equals(eventType)) {
            return Result.IGNORED;
        }

        JsonNode payload;
        try {
            payload = objectMapper.readTree(rawBody);
        } catch (Exception e) {
            log.warn("webhook payload 파싱 실패", e);
            return Result.IGNORED;
        }

        String action = text(payload, "action");
        if (!REVIEWABLE_ACTIONS.contains(action)) {
            return Result.IGNORED;
        }

        JsonNode repo = payload.get("repository");
        int prNumber = payload.path("number").asInt(0);
        String ownerRepo = repo != null ? text(repo, "full_name") : null;
        if (ownerRepo == null || prNumber <= 0) {
            log.warn("webhook payload에 repository.full_name 또는 number 누락");
            return Result.IGNORED;
        }

        Optional<PrWebhookTargetPort.Target> target = prWebhookTargetPort.resolve(ownerRepo);
        if (target.isEmpty()) {
            log.info("webhook 대상 프로젝트 없음 (또는 소유자 토큰 없음): repo={}", ownerRepo);
            return Result.IGNORED;
        }

        PrWebhookTargetPort.Target t = target.get();
        log.info("webhook PR 리뷰 트리거: repo={}, pr={}, action={}", ownerRepo, prNumber, action);
        prReviewRunner.reviewAsync(t.projectId(), prNumber, t.ownerId(), t.githubToken());
        return Result.ACCEPTED;
    }

    // JsonNode에서 텍스트 필드를 안전하게 추출 (없으면 null)
    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return (v == null || v.isNull()) ? null : v.asText();
    }
}
