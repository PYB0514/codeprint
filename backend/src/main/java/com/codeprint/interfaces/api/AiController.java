// AI 키 관리 + 노드/엣지 설명 + 그래프 누락 패턴 감지 API
package com.codeprint.interfaces.api;

import com.codeprint.application.ai.AiGraphAnalysisService;
import com.codeprint.application.ai.GraphEdgeDto;
import com.codeprint.application.ai.GraphNodeDto;
import com.codeprint.domain.ai.AiProvider;
import com.codeprint.domain.ai.UserAiKey;
import com.codeprint.domain.ai.UserAiKeyRepository;
import com.codeprint.domain.graph.GraphRepository;
import com.codeprint.infrastructure.ai.AiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
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
    private final List<AiService> aiServices;
    private final AiGraphAnalysisService aiGraphAnalysisService;
    private final GraphRepository graphRepository;

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

    // 등록된 AI 제공자 목록 조회 (키 값은 반환하지 않음)
    @GetMapping("/keys")
    public ResponseEntity<List<ProviderInfo>> getKeys(@AuthenticationPrincipal User user) {
        UUID userId = UUID.fromString(user.getUsername());
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
        UUID userId = UUID.fromString(user.getUsername());
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
        UUID userId = UUID.fromString(user.getUsername());
        AiProvider aiProvider = AiProvider.valueOf(provider.toUpperCase());
        aiKeyRepository.deleteByUserIdAndProvider(userId, aiProvider);
        return ResponseEntity.noContent().build();
    }

    // 노드/엣지 AI 설명 요청
    @PostMapping("/explain")
    public ResponseEntity<ExplainResponse> explain(
            @Valid @RequestBody ExplainRequest req,
            @AuthenticationPrincipal User user) {
        UUID userId = UUID.fromString(user.getUsername());
        AiProvider aiProvider = AiProvider.valueOf(req.provider().toUpperCase());
        UserAiKey key = aiKeyRepository.findByUserIdAndProvider(userId, aiProvider)
                .orElseThrow(() -> new IllegalArgumentException(req.provider() + " API 키가 등록되지 않았습니다."));

        AiService service = aiServices.stream()
                .filter(s -> s.provider() == aiProvider)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Unsupported provider: " + aiProvider));

        String prompt = buildPrompt(req);
        String explanation = service.explain(key.getApiKey(), prompt);
        return ResponseEntity.ok(new ExplainResponse(explanation));
    }

    // 함수 노드 컨텍스트로 코드 스텁 생성
    @PostMapping("/generate-code")
    public ResponseEntity<GenerateCodeResponse> generateCode(
            @Valid @RequestBody GenerateCodeRequest req,
            @AuthenticationPrincipal User user) {
        UUID userId = UUID.fromString(user.getUsername());
        AiProvider aiProvider = AiProvider.valueOf(req.provider().toUpperCase());
        UserAiKey key = aiKeyRepository.findByUserIdAndProvider(userId, aiProvider)
                .orElseThrow(() -> new IllegalArgumentException(req.provider() + " API 키가 등록되지 않았습니다."));

        AiService service = aiServices.stream()
                .filter(s -> s.provider() == aiProvider)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Unsupported provider: " + aiProvider));

        String lang = req.language() != null && !req.language().isBlank() ? req.language() : "java";
        String prompt = buildCodeGenPrompt(req, lang);
        String code = service.explain(key.getApiKey(), prompt);
        return ResponseEntity.ok(new GenerateCodeResponse(code, lang));
    }

    // 그래프 전체를 Claude AI로 분석하여 누락 패턴 감지
    @PostMapping("/graphs/{graphId}/analyze")
    public ResponseEntity<List<AiGraphAnalysisService.DetectedIssue>> analyzeGraph(
            @PathVariable UUID graphId,
            @AuthenticationPrincipal User user) {
        UUID userId = UUID.fromString(user.getUsername());
        var rawNodes = graphRepository.findNodesByGraphId(graphId);
        var rawEdges = graphRepository.findEdgesByGraphId(graphId);
        var nodes = rawNodes.stream()
                .map(n -> new GraphNodeDto(n.getId(), n.getType().name(), n.getName(), n.getMetadata()))
                .toList();
        var edges = rawEdges.stream()
                .map(e -> new GraphEdgeDto(e.getId(), e.getType().name(), e.getSourceNodeId(), e.getTargetNodeId()))
                .toList();
        List<AiGraphAnalysisService.DetectedIssue> issues = aiGraphAnalysisService.analyze(userId, nodes, edges);
        return ResponseEntity.ok(issues);
    }

    // 코드 생성 프롬프트 구성
    private String buildCodeGenPrompt(GenerateCodeRequest req, String lang) {
        StringBuilder sb = new StringBuilder();
        sb.append("다음은 소프트웨어 프로젝트의 함수 노드 정보입니다.\n\n");
        sb.append("함수명: ").append(req.nodeName()).append("\n");
        sb.append("언어: ").append(lang).append("\n");
        if (req.comment() != null && !req.comment().isBlank())
            sb.append("역할(주석): ").append(req.comment()).append("\n");
        if (req.callers() != null && !req.callers().isBlank())
            sb.append("이 함수를 호출하는 곳: ").append(req.callers()).append("\n");
        if (req.callees() != null && !req.callees().isBlank())
            sb.append("이 함수가 호출하는 곳: ").append(req.callees()).append("\n");
        sb.append("\n위 정보를 바탕으로 이 함수의 ").append(lang).append(" 구현 코드 스텁을 생성해주세요. ");
        sb.append("실제 구현 가능한 수준의 코드를 작성하되, 코드만 반환하고 설명은 생략하세요. ");
        sb.append("코드 블록(```")  .append(lang).append(" ... ```) 형식으로 반환하세요.");
        return sb.toString();
    }

    // 노드 컨텍스트를 기반으로 AI 프롬프트 구성
    private String buildPrompt(ExplainRequest req) {
        StringBuilder sb = new StringBuilder();
        sb.append("다음은 소프트웨어 프로젝트의 코드 구조 그래프에서 추출한 노드 정보입니다.\n\n");
        sb.append("노드명: ").append(req.nodeName()).append("\n");
        if (req.nodeType() != null) sb.append("타입: ").append(req.nodeType()).append("\n");
        if (req.comment() != null && !req.comment().isBlank())
            sb.append("주석: ").append(req.comment()).append("\n");
        if (req.callers() != null && !req.callers().isBlank())
            sb.append("호출하는 곳: ").append(req.callers()).append("\n");
        if (req.callees() != null && !req.callees().isBlank())
            sb.append("호출되는 곳: ").append(req.callees()).append("\n");
        sb.append("\n이 노드의 역할과 동작을 개발자가 이해하기 쉽게 한국어로 간결하게 설명해주세요. ");
        sb.append("3~5문장으로 작성하세요.");
        return sb.toString();
    }
}
