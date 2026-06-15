// WebhookSignatureVerifier 단위 테스트 — HMAC-SHA256 서명 검증 회귀 방지
package com.codeprint.domain.analysis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookSignatureVerifierTest {

    private static final String SECRET = "my-webhook-secret";
    private static final byte[] PAYLOAD = "{\"action\":\"opened\"}".getBytes(StandardCharsets.UTF_8);

    @Test
    @DisplayName("올바른 secret으로 계산한 서명은 검증을 통과한다")
    void verify_validSignature_returnsTrue() {
        String sig = "sha256=" + WebhookSignatureVerifier.hmacSha256Hex(SECRET, PAYLOAD);

        assertThat(WebhookSignatureVerifier.verify(SECRET, PAYLOAD, sig)).isTrue();
    }

    @Test
    @DisplayName("다른 secret으로 만든 서명은 거부한다")
    void verify_wrongSecret_returnsFalse() {
        String sig = "sha256=" + WebhookSignatureVerifier.hmacSha256Hex("other-secret", PAYLOAD);

        assertThat(WebhookSignatureVerifier.verify(SECRET, PAYLOAD, sig)).isFalse();
    }

    @Test
    @DisplayName("payload가 변조되면 서명이 일치하지 않아 거부한다")
    void verify_tamperedPayload_returnsFalse() {
        String sig = "sha256=" + WebhookSignatureVerifier.hmacSha256Hex(SECRET, PAYLOAD);
        byte[] tampered = "{\"action\":\"closed\"}".getBytes(StandardCharsets.UTF_8);

        assertThat(WebhookSignatureVerifier.verify(SECRET, tampered, sig)).isFalse();
    }

    @Test
    @DisplayName("secret이 비어있으면(미설정) 항상 거부한다")
    void verify_blankSecret_returnsFalse() {
        String sig = "sha256=" + WebhookSignatureVerifier.hmacSha256Hex(SECRET, PAYLOAD);

        assertThat(WebhookSignatureVerifier.verify("", PAYLOAD, sig)).isFalse();
        assertThat(WebhookSignatureVerifier.verify(null, PAYLOAD, sig)).isFalse();
    }

    @Test
    @DisplayName("서명 헤더가 없거나 sha256= 접두사가 아니면 거부한다")
    void verify_malformedHeader_returnsFalse() {
        String hex = WebhookSignatureVerifier.hmacSha256Hex(SECRET, PAYLOAD);

        assertThat(WebhookSignatureVerifier.verify(SECRET, PAYLOAD, null)).isFalse();
        assertThat(WebhookSignatureVerifier.verify(SECRET, PAYLOAD, hex)).isFalse();
        assertThat(WebhookSignatureVerifier.verify(SECRET, PAYLOAD, "sha1=" + hex)).isFalse();
    }
}
