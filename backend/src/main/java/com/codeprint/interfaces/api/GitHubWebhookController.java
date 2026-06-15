// GitHub webhook 수신 REST API — pull_request 이벤트를 서명 검증 후 PR 리뷰로 트리거 (Tier 1)
package com.codeprint.interfaces.api;

import com.codeprint.application.analysis.GitHubWebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/webhooks/github")
@RequiredArgsConstructor
public class GitHubWebhookController {

    private final GitHubWebhookService gitHubWebhookService;

    // GitHub webhook 수신 — raw body로 HMAC 서명을 검증하고 즉시 응답 (분석은 비동기)
    @PostMapping
    public ResponseEntity<Map<String, String>> receive(
            @RequestHeader(value = "X-GitHub-Event", required = false) String event,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody(required = false) byte[] rawBody) {

        GitHubWebhookService.Result result =
                gitHubWebhookService.handle(event, signature, rawBody == null ? new byte[0] : rawBody);

        return switch (result) {
            case UNAUTHORIZED -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("status", "invalid signature"));
            case IGNORED -> ResponseEntity.ok(Map.of("status", "ignored"));
            case ACCEPTED -> ResponseEntity.accepted().body(Map.of("status", "review queued"));
        };
    }
}
