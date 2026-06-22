// GlobalExceptionHandler 단위 테스트 — 표준 오류 응답·5xx 추적 ID(traceId) 부여 회귀 방지
package com.codeprint.interfaces.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @SuppressWarnings("unchecked")
    private Map<String, Object> body(ResponseEntity<Map<String, Object>> response) {
        return response.getBody();
    }

    @Test
    @DisplayName("500(처리되지 않은 예외) — traceId가 응답에 포함되고 표준 필드를 모두 가진다")
    void handleGeneric_includesTraceId() {
        ResponseEntity<Map<String, Object>> response = handler.handleGeneric(new RuntimeException("boom"));

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        Map<String, Object> body = body(response);
        assertThat(body).containsKeys("status", "message", "timestamp", "traceId");
        assertThat(body.get("message")).isEqualTo("Internal server error");
        assertThat((String) body.get("traceId")).isNotBlank();
    }

    @Test
    @DisplayName("400(잘못된 인자) — traceId는 부여하지 않는다(사용자 입력 오류)")
    void handleIllegalArgument_noTraceId() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleIllegalArgument(new IllegalArgumentException("bad input"));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        Map<String, Object> body = body(response);
        assertThat(body).containsKeys("status", "message", "timestamp");
        assertThat(body).doesNotContainKey("traceId");
        assertThat(body.get("message")).isEqualTo("bad input");
    }

    @Test
    @DisplayName("ResponseStatusException 5xx — traceId 부여")
    void handleResponseStatus_serverError_includesTraceId() {
        ResponseEntity<Map<String, Object>> response = handler.handleResponseStatus(
                new ResponseStatusException(HttpStatus.BAD_GATEWAY, "upstream down"));

        assertThat(response.getStatusCode().value()).isEqualTo(502);
        assertThat(body(response)).containsKey("traceId");
        assertThat((String) body(response).get("traceId")).isNotBlank();
    }

    @Test
    @DisplayName("ResponseStatusException 4xx — traceId 미부여")
    void handleResponseStatus_clientError_noTraceId() {
        ResponseEntity<Map<String, Object>> response = handler.handleResponseStatus(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "missing"));

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(body(response)).doesNotContainKey("traceId");
    }

    @Test
    @DisplayName("매 호출마다 서로 다른 traceId가 생성된다")
    void traceId_isUniquePerCall() {
        String first = (String) body(handler.handleGeneric(new RuntimeException("a"))).get("traceId");
        String second = (String) body(handler.handleGeneric(new RuntimeException("b"))).get("traceId");

        assertThat(first).isNotEqualTo(second);
    }
}
