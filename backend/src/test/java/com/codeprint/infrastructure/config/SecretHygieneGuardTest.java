// SecretHygieneGuard 단위 테스트 — 운영 환경 기본 시크릿 노출 회귀 방지
package com.codeprint.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SecretHygieneGuardTest {

    private static final String DEFAULT_JWT_SECRET = "local-dev-secret-key-must-be-at-least-32-characters";
    private static final String DEFAULT_ENCRYPTION_KEY = "Y29kZXByaW50LWxvY2FsLWRldi1lbmNyeXB0LWtleSE=";
    private static final String REAL_SECRET = "prod-secret-generated-by-someone-not-in-the-repo";

    private Environment envWithProfiles(String... profiles) {
        Environment env = mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(profiles);
        return env;
    }

    @Test
    @DisplayName("local 프로파일이 아니고 JWT 시크릿이 기본값이면 기동 실패")
    void nonLocalProfile_defaultJwtSecret_throws() {
        assertThatThrownBy(() ->
                new SecretHygieneGuard(envWithProfiles(), DEFAULT_JWT_SECRET, REAL_SECRET))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("local 프로파일이 아니고 암호화 키가 기본값이면 기동 실패")
    void nonLocalProfile_defaultEncryptionKey_throws() {
        assertThatThrownBy(() ->
                new SecretHygieneGuard(envWithProfiles(), REAL_SECRET, DEFAULT_ENCRYPTION_KEY))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("local 프로파일이 아니어도 실제 시크릿이면 정상 기동")
    void nonLocalProfile_realSecrets_succeeds() {
        assertThatCode(() ->
                new SecretHygieneGuard(envWithProfiles(), REAL_SECRET, REAL_SECRET))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("local 프로파일이면 기본값이어도 정상 기동")
    void localProfile_defaultSecrets_succeeds() {
        assertThatCode(() ->
                new SecretHygieneGuard(envWithProfiles("local"), DEFAULT_JWT_SECRET, DEFAULT_ENCRYPTION_KEY))
                .doesNotThrowAnyException();
    }
}
