// AiFailoverClient 단위 테스트 — 인증/quota 오류 시 다음 프로바이더 전환, 그 외 오류는 즉시 전파 회귀 방지
package com.codeprint.application.graph;

import com.codeprint.infrastructure.ai.AiService;
import com.codeprint.shared.ai.AiProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiFailoverClientTest {

    @Mock private AiService anthropicService;
    @Mock private AiService geminiService;

    private final Map<AiProvider, String> keys = Map.of(
            AiProvider.ANTHROPIC, "anthropic-key",
            AiProvider.GEMINI, "gemini-key");

    private AiFailoverClient client(List<AiProvider> candidates) {
        Map<AiProvider, AiService> services = Map.of(
                AiProvider.ANTHROPIC, anthropicService,
                AiProvider.GEMINI, geminiService);
        return new AiFailoverClient(candidates,
                p -> Optional.ofNullable(keys.get(p)), services::get);
    }

    @Test
    @DisplayName("1차 프로바이더 성공 시 전환 없음")
    void firstProviderSucceeds_noSwitch() {
        when(anthropicService.generate("anthropic-key", "prompt")).thenReturn("결과");
        AiFailoverClient client = client(List.of(AiProvider.ANTHROPIC, AiProvider.GEMINI));

        String result = client.generate("prompt");

        assertThat(result).isEqualTo("결과");
        assertThat(client.switched()).isFalse();
        verify(geminiService, never()).generate(anyString(), anyString());
    }

    @Test
    @DisplayName("1차 프로바이더 429 실패 시 2차로 전환해 성공")
    void firstProvider429_switchesToSecond() {
        HttpClientErrorException quotaError = HttpClientErrorException.create(
                HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests", HttpHeaders.EMPTY, new byte[0], null);
        when(anthropicService.generate("anthropic-key", "prompt")).thenThrow(quotaError);
        when(geminiService.generate("gemini-key", "prompt")).thenReturn("결과");
        AiFailoverClient client = client(List.of(AiProvider.ANTHROPIC, AiProvider.GEMINI));

        String result = client.generate("prompt");

        assertThat(result).isEqualTo("결과");
        assertThat(client.switched()).isTrue();
        assertThat(client.originalProvider()).isEqualTo(AiProvider.ANTHROPIC);
        assertThat(client.currentProvider()).isEqualTo(AiProvider.GEMINI);
    }

    @Test
    @DisplayName("401·403도 전환 대상")
    void authErrors_alsoTriggerSwitch() {
        HttpClientErrorException authError = HttpClientErrorException.create(
                HttpStatus.UNAUTHORIZED, "Unauthorized", HttpHeaders.EMPTY, new byte[0], null);
        when(anthropicService.generate("anthropic-key", "prompt")).thenThrow(authError);
        when(geminiService.generate("gemini-key", "prompt")).thenReturn("결과");
        AiFailoverClient client = client(List.of(AiProvider.ANTHROPIC, AiProvider.GEMINI));

        assertThat(client.generate("prompt")).isEqualTo("결과");
        assertThat(client.switched()).isTrue();
    }

    @Test
    @DisplayName("5xx 등 전환 대상 아닌 오류는 즉시 전파, 전환 없음")
    void nonFailoverStatus_propagatesImmediately() {
        HttpServerErrorException serverError = HttpServerErrorException.create(
                HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", HttpHeaders.EMPTY, new byte[0], null);
        when(anthropicService.generate("anthropic-key", "prompt")).thenThrow(serverError);
        AiFailoverClient client = client(List.of(AiProvider.ANTHROPIC, AiProvider.GEMINI));

        assertThatThrownBy(() -> client.generate("prompt")).isSameAs(serverError);
        assertThat(client.switched()).isFalse();
        verify(geminiService, never()).generate(anyString(), anyString());
    }

    @Test
    @DisplayName("HTTP 예외가 아닌 오류(파싱 실패 등)도 즉시 전파")
    void nonHttpException_propagatesImmediately() {
        RuntimeException parseError = new RuntimeException("파싱 실패");
        when(anthropicService.generate("anthropic-key", "prompt")).thenThrow(parseError);
        AiFailoverClient client = client(List.of(AiProvider.ANTHROPIC, AiProvider.GEMINI));

        assertThatThrownBy(() -> client.generate("prompt")).isSameAs(parseError);
        assertThat(client.switched()).isFalse();
    }

    @Test
    @DisplayName("마지막 후보까지 전부 429면 마지막 예외가 전파")
    void allCandidatesExhausted_propagatesLastException() {
        HttpClientErrorException quotaError1 = HttpClientErrorException.create(
                HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests", HttpHeaders.EMPTY, new byte[0], null);
        HttpClientErrorException quotaError2 = HttpClientErrorException.create(
                HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests", HttpHeaders.EMPTY, new byte[0], null);
        when(anthropicService.generate("anthropic-key", "prompt")).thenThrow(quotaError1);
        when(geminiService.generate("gemini-key", "prompt")).thenThrow(quotaError2);
        AiFailoverClient client = client(List.of(AiProvider.ANTHROPIC, AiProvider.GEMINI));

        assertThatThrownBy(() -> client.generate("prompt")).isSameAs(quotaError2);
        assertThat(client.switched()).isTrue();
    }

    @Test
    @DisplayName("키가 없는 후보는 실패 로그 없이 건너뜀")
    void candidateWithoutKey_skippedSilently() {
        when(geminiService.generate("gemini-key", "prompt")).thenReturn("결과");
        AiFailoverClient client = client(List.of(AiProvider.OPENAI, AiProvider.GEMINI));

        assertThat(client.generate("prompt")).isEqualTo("결과");
        assertThat(client.switched()).isTrue();
    }

    @Test
    @DisplayName("orderedCandidates — 요청 프로바이더가 맨 앞, 나머지는 등록순으로 중복 없이 이어붙임")
    void orderedCandidates_requestedFirst_dedupedRest() {
        List<AiProvider> result = AiFailoverClient.orderedCandidates(
                AiProvider.GEMINI, List.of(AiProvider.ANTHROPIC, AiProvider.GEMINI, AiProvider.OPENAI));

        assertThat(result).containsExactly(AiProvider.GEMINI, AiProvider.ANTHROPIC, AiProvider.OPENAI);
    }
}
