// Refresh Token 저장소 인터페이스
package com.codeprint.domain.user;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository {

    // 토큰 해시로 조회
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    // 저장
    RefreshToken save(RefreshToken refreshToken);

    // 사용자의 모든 Refresh Token 삭제 (로그아웃)
    void deleteAllByUserId(UUID userId);

    // 특정 토큰 해시 삭제
    void deleteByTokenHash(String tokenHash);
}
