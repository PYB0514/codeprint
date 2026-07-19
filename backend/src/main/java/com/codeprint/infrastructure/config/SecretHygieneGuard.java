// local 프로파일이 아닌데 JWT/암호화 키가 공개 저장소 기본값이면 기동을 즉시 실패시키는 가드
package com.codeprint.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;

@Component
public class SecretHygieneGuard {

    private static final String LOCAL_PROFILE = "local";
    private static final Set<String> DEFAULT_SECRETS = Set.of(
            "local-dev-secret-key-must-be-at-least-32-characters",
            "Y29kZXByaW50LWxvY2FsLWRldi1lbmNyeXB0LWtleSE=");

    public SecretHygieneGuard(Environment environment,
                               @Value("${jwt.secret}") String jwtSecret,
                               @Value("${encryption.key}") String encryptionKey) {
        boolean isLocal = Arrays.asList(environment.getActiveProfiles()).contains(LOCAL_PROFILE);
        if (!isLocal && (DEFAULT_SECRETS.contains(jwtSecret) || DEFAULT_SECRETS.contains(encryptionKey))) {
            throw new IllegalStateException(
                    "JWT_SECRET 또는 ENCRYPTION_KEY가 로컬 개발용 기본값입니다 — 운영 환경변수를 설정하세요.");
        }
    }
}
