// AI 키 관리 + 노드/엣지 설명 + 코드 생성 API
package com.codeprint.interfaces.api;

import com.codeprint.application.ai.AiExplainService;
import com.codeprint.domain.ai.AiProvider;
import com.codeprint.domain.ai.UserAiKey;
import com.codeprint.domain.ai.UserAiKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.codeprint.domain.user.User;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final UserAiKeyRepository aiKeyRepository;
    private final AiExplainService aiExplainService;

    record SaveKeyRequest(@NotBlank String apiKey) {}
    record ExplainRequest(@NotBlank String provider, @NotBlank String nodeId,
                          @NotBlank String nodeName, String nodeType, String comment,
                          String callers, String callees) {}
    record ExplainResponse(String explanation) {}
    record GenerateCodeRequest(@NotBlank String provider, @NotBlank String nodeName,
                               String nodeType, String comment,
                               String callers, String callees, String language) {}
    record GenerateCodeResponse(String code, String language) {}
    record ProviderInfo(String provider, boolean registered) {}
    record ExplainWarningRequest(@NotBlank String provider, @NotBlank String warningType,
                                 @NotBlank String severity, @NotBlank String message, String guide) {}
    record ExplainWarningResponse(String explanation) {}

    // 등록된 AI 제공자 목록 조회 (키 값은 반환하지 않음)
    @GetMapping("/keys")
    public ResponseEntity<List<ProviderInfo>> getKeys(@AuthenticationPrincipal User user) {
        UUID userId = user.getId();
        List<UserAiKey> keys = aiKeyRepository.findByUserId(userId);
        List<String> registered = keys.stream().map(k -> k.getProvider().name()).toList();
        List<ProviderInfo> result = List.of(AiProvider.values()).stream()
                .map(p -> new ProviderInfo(p.name(), registered.contains(p.name())))
                .toList();
        return ResponseEntity.ok(result);
    }

    // AI 제공자 API 키 저장 (이미 있으면 갱신)
    @PutMapping("/keys/{provider}")
    public ResponseEntity<Void> saveKey(
            @PathVariable String provider,
            @Valid @RequestBody SaveKeyRequest req,
            @AuthenticationPrincipal User user) {
        UUID userId = user.getId();
        AiProvider aiProvider = AiProvider.valueOf(provider.toUpperCase());
        aiKeyRepository.findByUserIdAndProvider(userId, aiProvider)
                .ifPresentOrElse(
                        key -> { key.updateApiKey(req.apiKey()); aiKeyRepository.save(key); },
                        () -> aiKeyRepository.save(UserAiKey.of(userId, aiProvider, req.apiKey()))
                );
        return ResponseEntity.ok().build();
    }

    // AI 제공자 API 키 삭제
    @DeleteMapping("/keys/{provider}")
    public ResponseEntity<Void> deleteKey(
            @PathVariable String provider,
            @AuthenticationPrincipal User user) {
        UUID userId = user.getId();
        AiProvider aiProvider = AiProvider.valueOf(provider.toUpperCase());
        aiKeyRepository.deleteByUserIdAndProvider(userId, aiProvider);
        return ResponseEntity.noContent().build();
    }

    // 노드/엣지 AI 설명 요청
    @PostMapping("/explain")
    public ResponseEntity<ExplainResponse> explain(
            @Valid @RequestBody ExplainRequest req,
            @AuthenticationPrincipal User user) {
        AiProvider aiProvider = AiProvider.valueOf(req.provider().toUpperCase());
        String explanation = aiExplainService.explainNode(user.getId(), aiProvider,
                req.nodeName(), req.nodeType(), req.comment(), req.callers(), req.callees());
        return ResponseEntity.ok(new ExplainResponse(explanation));
    }

    // 함수 노드 컨텍스트로 코드 스텁 생성
    @PostMapping("/generate-code")
    public ResponseEntity<GenerateCodeResponse> generateCode(
            @Valid @RequestBody GenerateCodeRequest req,
            @AuthenticationPrincipal User user) {
        AiProvider aiProvider = AiProvider.valueOf(req.provider().toUpperCase());
        String lang = req.language() != null && !req.language().isBlank() ? req.language() : "java";
        String code = aiExplainService.generateCode(user.getId(), aiProvider,
                req.nodeName(), req.nodeType(), req.comment(), req.callers(), req.callees(), lang);
        return ResponseEntity.ok(new GenerateCodeResponse(code, lang));
    }

    // 구조 경고 AI 설명·수정안 요청 — 판정 자체는 불변, AI는 자문(advisory)일 뿐
    @PostMapping("/explain-warning")
    public ResponseEntity<ExplainWarningResponse> explainWarning(
            @Valid @RequestBody ExplainWarningRequest req,
            @AuthenticationPrincipal User user) {
        AiProvider aiProvider = AiProvider.valueOf(req.provider().toUpperCase());
        String explanation = aiExplainService.explainWarning(user.getId(), aiProvider,
                req.warningType(), req.severity(), req.message(), req.guide());
        return ResponseEntity.ok(new ExplainWarningResponse(explanation));
    }
}
