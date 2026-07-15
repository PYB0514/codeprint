// GitHub Actions cron이 보내는 공유 시크릿 헤더를 상수시간으로 검증하는 순수 도메인 함수
package com.codeprint.domain.analysis;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class CronSecretVerifier {

    private CronSecretVerifier() {}

    // 서버에 설정된 secret과 요청 헤더 값이 일치하는지 검증
    public static boolean verify(String configuredSecret, String requestSecret) {
        if (configuredSecret == null || configuredSecret.isBlank()) return false;
        if (requestSecret == null || requestSecret.isBlank()) return false;

        return MessageDigest.isEqual(
                configuredSecret.getBytes(StandardCharsets.UTF_8),
                requestSecret.getBytes(StandardCharsets.UTF_8));
    }
}
