// 팀 API 키 발급·목록·폐기 REST API 컨트롤러
package com.codeprint.interfaces.api;

import com.codeprint.application.team.TeamApiKeyApplicationService;
import com.codeprint.domain.team.TeamApiKey;
import com.codeprint.domain.user.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/teams/{teamId}/api-keys")
@RequiredArgsConstructor
public class TeamApiKeyController {

    private final TeamApiKeyApplicationService apiKeyService;

    // API 키 발급 — 평문은 이 응답에서만 노출(재조회 불가)
    @PostMapping
    public ResponseEntity<IssuedKeyResponse> issueKey(
            @PathVariable UUID teamId,
            @Valid @RequestBody IssueKeyRequest req,
            @AuthenticationPrincipal User user) {
        TeamApiKey.IssuedKey issued = apiKeyService.issueKey(teamId, user.getId(), req.name());
        return ResponseEntity.status(201).body(new IssuedKeyResponse(
                issued.entity().getId(), issued.entity().getName(), issued.rawKey(),
                issued.entity().getKeyPrefix(), issued.entity().getCreatedAt()));
    }

    // API 키 목록 — 평문 노출 없이 prefix만 반환
    @GetMapping
    public ResponseEntity<java.util.List<KeyResponse>> getKeys(
            @PathVariable UUID teamId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(apiKeyService.getKeys(teamId, user.getId()).stream()
                .map(k -> new KeyResponse(k.getId(), k.getName(), k.getKeyPrefix(),
                        k.getCreatedAt(), k.getLastUsedAt(), k.isRevoked()))
                .toList());
    }

    // API 키 폐기
    @DeleteMapping("/{keyId}")
    public ResponseEntity<Void> revokeKey(
            @PathVariable UUID teamId,
            @PathVariable UUID keyId,
            @AuthenticationPrincipal User user) {
        apiKeyService.revokeKey(teamId, user.getId(), keyId);
        return ResponseEntity.noContent().build();
    }

    record IssueKeyRequest(@NotBlank String name) {}
    record IssuedKeyResponse(UUID id, String name, String rawKey, String keyPrefix, Instant createdAt) {}
    record KeyResponse(UUID id, String name, String keyPrefix, Instant createdAt, Instant lastUsedAt, boolean revoked) {}
}
