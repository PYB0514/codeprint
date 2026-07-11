// AI 제공자 선택·프롬프트 조립·호출을 담당하는 응용 서비스 (Controller가 infrastructure.ai.AiService를 직접 의존하지 않도록 분리)
package com.codeprint.application.ai;

import com.codeprint.domain.ai.AiProvider;
import com.codeprint.domain.ai.UserAiKey;
import com.codeprint.domain.ai.UserAiKeyRepository;
import com.codeprint.infrastructure.ai.AiService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiExplainService {

    private final UserAiKeyRepository aiKeyRepository;
    private final List<AiService> aiServices;

    // 노드 컨텍스트로 AI 설명 생성
    public String explainNode(UUID userId, AiProvider provider, String nodeName, String nodeType,
                              String comment, String callers, String callees) {
        String apiKey = apiKeyOf(userId, provider);
        String prompt = buildPrompt(nodeName, nodeType, comment, callers, callees);
        return resolveService(provider).explain(apiKey, prompt);
    }

    // 함수 노드 컨텍스트로 코드 스텁 생성
    public String generateCode(UUID userId, AiProvider provider, String nodeName, String nodeType,
                               String comment, String callers, String callees, String language) {
        String apiKey = apiKeyOf(userId, provider);
        String prompt = buildCodeGenPrompt(nodeName, comment, callers, callees, language);
        return resolveService(provider).explain(apiKey, prompt);
    }

    private String apiKeyOf(UUID userId, AiProvider provider) {
        UserAiKey key = aiKeyRepository.findByUserIdAndProvider(userId, provider)
                .orElseThrow(() -> new IllegalArgumentException(provider + " API 키가 등록되지 않았습니다."));
        return key.getApiKey();
    }

    private AiService resolveService(AiProvider provider) {
        return aiServices.stream()
                .filter(s -> s.provider() == provider)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Unsupported provider: " + provider));
    }

    // 노드 컨텍스트를 기반으로 AI 설명 프롬프트 구성
    private String buildPrompt(String nodeName, String nodeType, String comment, String callers, String callees) {
        StringBuilder sb = new StringBuilder();
        sb.append("다음은 소프트웨어 프로젝트의 코드 구조 그래프에서 추출한 노드 정보입니다.\n\n");
        sb.append("노드명: ").append(nodeName).append("\n");
        if (nodeType != null) sb.append("타입: ").append(nodeType).append("\n");
        if (comment != null && !comment.isBlank())
            sb.append("주석: ").append(comment).append("\n");
        if (callers != null && !callers.isBlank())
            sb.append("호출하는 곳: ").append(callers).append("\n");
        if (callees != null && !callees.isBlank())
            sb.append("호출되는 곳: ").append(callees).append("\n");
        sb.append("\n이 노드의 역할과 동작을 개발자가 이해하기 쉽게 한국어로 간결하게 설명해주세요. ");
        sb.append("3~5문장으로 작성하세요.");
        return sb.toString();
    }

    // 코드 생성 프롬프트 구성
    private String buildCodeGenPrompt(String nodeName, String comment, String callers, String callees, String lang) {
        StringBuilder sb = new StringBuilder();
        sb.append("다음은 소프트웨어 프로젝트의 함수 노드 정보입니다.\n\n");
        sb.append("함수명: ").append(nodeName).append("\n");
        sb.append("언어: ").append(lang).append("\n");
        if (comment != null && !comment.isBlank())
            sb.append("역할(주석): ").append(comment).append("\n");
        if (callers != null && !callers.isBlank())
            sb.append("이 함수를 호출하는 곳: ").append(callers).append("\n");
        if (callees != null && !callees.isBlank())
            sb.append("이 함수가 호출하는 곳: ").append(callees).append("\n");
        sb.append("\n위 정보를 바탕으로 이 함수의 ").append(lang).append(" 구현 코드 스텁을 생성해주세요. ");
        sb.append("실제 구현 가능한 수준의 코드를 작성하되, 코드만 반환하고 설명은 생략하세요. ");
        sb.append("코드 블록(```").append(lang).append(" ... ```) 형식으로 반환하세요.");
        return sb.toString();
    }
}
