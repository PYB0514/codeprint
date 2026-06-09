// RefreshTokenRepository 구현체
package com.codeprint.infrastructure.persistence.user;

import com.codeprint.domain.user.RefreshToken;
import com.codeprint.domain.user.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class RefreshTokenRepositoryImpl implements RefreshTokenRepository {

    private final RefreshTokenJpaRepository jpa;

    // 토큰 해시로 조회
    @Override
    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        return jpa.findByTokenHash(tokenHash);
    }

    // 저장
    @Override
    public RefreshToken save(RefreshToken refreshToken) {
        return jpa.save(refreshToken);
    }

    // 사용자의 모든 Refresh Token 삭제
    @Override
    public void deleteAllByUserId(UUID userId) {
        jpa.deleteAllByUserId(userId);
    }

    // 특정 토큰 해시 삭제
    @Override
    public void deleteByTokenHash(String tokenHash) {
        jpa.deleteByTokenHash(tokenHash);
    }
}
