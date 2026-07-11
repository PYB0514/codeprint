// Access/Refresh Token 발급·회전·무효화 애플리케이션 서비스
package com.codeprint.application.user;

import com.codeprint.domain.user.RefreshToken;
import com.codeprint.domain.user.RefreshTokenRepository;
import com.codeprint.domain.user.User;
import com.codeprint.domain.user.UserRepository;
import com.codeprint.infrastructure.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthTokenService {

    // Refresh Token 유효 기간: 7일
    private static final long REFRESH_TOKEN_EXPIRY_SECONDS = 7 * 24 * 60 * 60L;

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    // 사용자 정보로 Access Token 발급
    public String issueAccessToken(UUID userId, String email, String role) {
        return jwtTokenProvider.generateToken(userId, email, role);
    }

    // Refresh Token 검증 후 기존 토큰을 새 토큰으로 교체 (Refresh Token Rotation)
    public Optional<TokenPair> rotateRefreshToken(String rawRefreshToken) {
        String tokenHash = jwtTokenProvider.hashRefreshToken(rawRefreshToken);
        Optional<RefreshToken> storedOpt = refreshTokenRepository.findByTokenHash(tokenHash);
        if (storedOpt.isEmpty() || storedOpt.get().isExpired()) {
            return Optional.empty();
        }

        RefreshToken stored = storedOpt.get();
        return userRepository.findById(stored.getUserId())
                .map(user -> issueRotatedTokenPair(user, tokenHash));
    }

    // 기존 토큰 삭제 후 새 Access/Refresh Token 쌍 발급
    private TokenPair issueRotatedTokenPair(User user, String oldTokenHash) {
        refreshTokenRepository.deleteByTokenHash(oldTokenHash);

        String newRawToken = jwtTokenProvider.generateRefreshToken();
        String newHash = jwtTokenProvider.hashRefreshToken(newRawToken);
        refreshTokenRepository.save(RefreshToken.create(user.getId(), newHash,
                Instant.now().plusSeconds(REFRESH_TOKEN_EXPIRY_SECONDS)));

        String newJwt = jwtTokenProvider.generateToken(user.getId(), user.getEmail(), user.getRole().name());
        return new TokenPair(newJwt, newRawToken);
    }

    // Refresh Token을 DB에서 무효화 — 로그아웃/계정탈퇴 시 사용
    public void revokeRefreshToken(String rawRefreshToken) {
        if (rawRefreshToken == null) return;
        refreshTokenRepository.deleteByTokenHash(jwtTokenProvider.hashRefreshToken(rawRefreshToken));
    }

    public record TokenPair(String accessToken, String refreshToken) {}
}
