// CronSecretVerifier 단위 테스트 — 외부 cron 트리거 공유 시크릿 검증 회귀 방지
package com.codeprint.domain.analysis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CronSecretVerifierTest {

    private static final String SECRET = "my-cron-secret";

    @Test
    @DisplayName("설정된 secret과 헤더 값이 일치하면 통과한다")
    void verify_matchingSecret_returnsTrue() {
        assertThat(CronSecretVerifier.verify(SECRET, SECRET)).isTrue();
    }

    @Test
    @DisplayName("헤더 값이 다르면 거부한다")
    void verify_wrongHeader_returnsFalse() {
        assertThat(CronSecretVerifier.verify(SECRET, "wrong-secret")).isFalse();
    }

    @Test
    @DisplayName("서버 secret이 비어있으면(미설정) 항상 거부한다")
    void verify_blankConfiguredSecret_returnsFalse() {
        assertThat(CronSecretVerifier.verify("", SECRET)).isFalse();
        assertThat(CronSecretVerifier.verify(null, SECRET)).isFalse();
    }

    @Test
    @DisplayName("요청 헤더 값이 없으면 거부한다")
    void verify_blankHeader_returnsFalse() {
        assertThat(CronSecretVerifier.verify(SECRET, null)).isFalse();
        assertThat(CronSecretVerifier.verify(SECRET, "")).isFalse();
    }
}
