// Anthropic Claude API 호출 구현체
package com.codeprint.infrastructure.ai;

import com.codeprint.domain.ai.AiProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class ClaudeAiService implements AiService {

    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String MODEL = "claude-haiku-4-5-20251001";

    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public AiProvider provider() {
        return AiProvider.CLAUDE;
    }

    // Anthropic Messages API 호출 후 응답 텍스트 반환
    @Override
    public String explain(String apiKey, String prompt) {
        Map<String, Object> body = Map.of(
                "model", MODEL,
                "max_tokens", 1024,
                "messages", List.of(Map.of("role", "user", "content", prompt))
        );
        try {
            String response = restClient.post()
                    .uri(API_URL)
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            JsonNode node = objectMapper.readTree(response);
            return node.path("content").get(0).path("text").asText();
        } catch (Exception e) {
            throw new RuntimeException("Claude API 호출 실패: " + e.getMessage(), e);
        }
    }
}
