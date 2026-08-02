// 사용자 BYOK(LLM API 키) 등록/상태조회/삭제 REST API — 평문 키는 절대 응답에 포함하지 않음
package com.codeprint.interfaces.api;

import com.codeprint.application.user.UserAiKeyService;
import com.codeprint.shared.ai.AiProvider;
import com.codeprint.domain.user.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users/me/ai-key")
@RequiredArgsConstructor
public class UserAiKeyController {

    private final UserAiKeyService userAiKeyService;

    // 등록 상태 조회 — 등록된 제공자 목록(평문 키는 반환하지 않음)
    @GetMapping
    public ResponseEntity<Map<String, Object>> status(@AuthenticationPrincipal User user) {
        List<AiProvider> providers = userAiKeyService.getRegisteredProviders(user.getId());
        return ResponseEntity.ok(Map.of("hasKey", !providers.isEmpty(), "providers", providers));
    }

    // 신규 등록 또는 회전
    @PutMapping
    public ResponseEntity<Void> register(
            @Valid @RequestBody RegisterKeyRequest request,
            @AuthenticationPrincipal User user) {
        userAiKeyService.registerOrRotate(user.getId(), request.provider(), request.apiKey());
        return ResponseEntity.noContent().build();
    }

    // 삭제
    @DeleteMapping("/{provider}")
    public ResponseEntity<Void> delete(
            @PathVariable AiProvider provider,
            @AuthenticationPrincipal User user) {
        userAiKeyService.delete(user.getId(), provider);
        return ResponseEntity.noContent().build();
    }

    // 키 등록 요청 DTO — 실제 제공자 키는 전부 200자 미만이라 300자를 상한으로 넉넉히 설정(암호화 후 DB 저장되는 임의 크기 문자열 방지)
    public record RegisterKeyRequest(@NotNull AiProvider provider, @NotBlank @Size(max = 300) String apiKey) {}
}
