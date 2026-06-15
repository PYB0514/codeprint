// GitHub webhook의 HMAC-SHA256 서명을 상수시간으로 검증하는 순수 도메인 함수
package com.codeprint.domain.analysis;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class WebhookSignatureVerifier {

    private WebhookSignatureVerifier() {}

    // X-Hub-Signature-256 헤더(sha256=...)가 secret으로 계산한 payload HMAC과 일치하는지 검증
    public static boolean verify(String secret, byte[] payload, String signatureHeader) {
        if (secret == null || secret.isBlank()) return false;
        if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) return false;
        if (payload == null) return false;

        String expected = "sha256=" + hmacSha256Hex(secret, payload);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signatureHeader.getBytes(StandardCharsets.UTF_8));
    }

    // secret 키로 payload의 HMAC-SHA256을 계산해 소문자 16진 문자열로 반환
    static String hmacSha256Hex(String secret, byte[] payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 계산 실패", e);
        }
    }
}
