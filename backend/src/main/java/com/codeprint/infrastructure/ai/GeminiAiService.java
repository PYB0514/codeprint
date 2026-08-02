// Google Gemini API 호출 구현체
package com.codeprint.infrastructure.ai;

import com.codeprint.shared.ai.AiProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class GeminiAiService implements AiService {

    private static final String API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent";

    private final RestClient restClient = AiRestClients.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public AiProvider provider() {
        return AiProvider.GEMINI;
    }

    // Google Gemini generateContent API 호출 후 응답 텍스트 반환
    @Override
    public String generate(String apiKey, String prompt) {
        Map<String, Object> body = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(Map.of("text", prompt)))
                )
        );
        try {
            // API 키를 URL 쿼리 파라미터가 아니라 헤더로 전달 — 쿼리 파라미터는 RestClient 예외 메시지·프록시
            // 로그 등에 URI 전체가 노출될 수 있어(로그 warn에 예외를 그대로 남기는 호출부가 있음) 헤더가 더 안전
            String response = restClient.post()
                    .uri(API_URL)
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            JsonNode node = objectMapper.readTree(response);
            return node.path("candidates").get(0)
                    .path("content").path("parts").get(0).path("text").asText();
        } catch (Exception e) {
            throw new RuntimeException("Gemini API 호출 실패: " + e.getMessage(), e);
        }
    }
}
