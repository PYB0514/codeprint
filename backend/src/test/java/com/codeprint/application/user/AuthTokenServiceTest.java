// AuthTokenService 단위 테스트 — Refresh Token 회전/무효화 회귀 방지 (반복-A 관련 파일)
package com.codeprint.application.user;

import com.codeprint.domain.user.RefreshToken;
import com.codeprint.domain.user.RefreshTokenRepository;
import com.codeprint.domain.user.User;
import com.codeprint.domain.user.UserRepository;
import com.codeprint.infrastructure.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthTokenServiceTest {

    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private UserRepository userRepository;

    private AuthTokenService service;

    @BeforeEach
    void setUp() {
        service = new AuthTokenService(jwtTokenProvider, refreshTokenRepository, userRepository);
    }

    // 유효한 Refresh Token이면 기존 토큰을 삭제하고 새 Access/Refresh Token 쌍을 발급한다
    @Test
    @DisplayName("rotateRefreshToken — 유효한 토큰이면 새 토큰 쌍 발급 후 기존 토큰 삭제")
    void rotateRefreshToken_valid_issuesNewPairAndDeletesOld() {
        UUID userId = UUID.randomUUID();
        User user = User.create(1L, "e@x.com", "kim");
        RefreshToken stored = RefreshToken.create(userId, "old-hash", Instant.now().plusSeconds(3600));

        when(jwtTokenProvider.hashRefreshToken("raw-old")).thenReturn("old-hash");
        when(refreshTokenRepository.findByTokenHash("old-hash")).thenReturn(Optional.of(stored));
        when(userRepository.findById(stored.getUserId())).thenReturn(Optional.of(user));
        when(jwtTokenProvider.generateRefreshToken()).thenReturn("raw-new");
        when(jwtTokenProvider.hashRefreshToken("raw-new")).thenReturn("new-hash");
        when(jwtTokenProvider.generateToken(any(), anyString(), anyString())).thenReturn("new-jwt");

        Optional<AuthTokenService.TokenPair> result = service.rotateRefreshToken("raw-old");

        assertThat(result).isPresent();
        assertThat(result.get().accessToken()).isEqualTo("new-jwt");
        assertThat(result.get().refreshToken()).isEqualTo("raw-new");
        verify(refreshTokenRepository).deleteByTokenHash("old-hash");
        verify(refreshTokenRepository).save(argThat(rt -> rt.getTokenHash().equals("new-hash")));
    }

    // 만료된 Refresh Token이면 빈 Optional을 반환하고 아무 것도 발급하지 않는다
    @Test
    @DisplayName("rotateRefreshToken — 만료된 토큰이면 빈 Optional")
    void rotateRefreshToken_expired_returnsEmpty() {
        UUID userId = UUID.randomUUID();
        RefreshToken expired = RefreshToken.create(userId, "old-hash", Instant.now().minusSeconds(1));

        when(jwtTokenProvider.hashRefreshToken("raw-old")).thenReturn("old-hash");
        when(refreshTokenRepository.findByTokenHash("old-hash")).thenReturn(Optional.of(expired));

        Optional<AuthTokenService.TokenPair> result = service.rotateRefreshToken("raw-old");

        assertThat(result).isEmpty();
        verify(refreshTokenRepository, never()).deleteByTokenHash(anyString());
        verify(refreshTokenRepository, never()).save(any());
    }

    // DB에 없는 토큰이면 빈 Optional을 반환한다
    @Test
    @DisplayName("rotateRefreshToken — DB에 없는 토큰이면 빈 Optional")
    void rotateRefreshToken_notFound_returnsEmpty() {
        when(jwtTokenProvider.hashRefreshToken("raw-unknown")).thenReturn("unknown-hash");
        when(refreshTokenRepository.findByTokenHash("unknown-hash")).thenReturn(Optional.empty());

        Optional<AuthTokenService.TokenPair> result = service.rotateRefreshToken("raw-unknown");

        assertThat(result).isEmpty();
    }

    // 토큰은 유효하지만 연결된 사용자가 이미 삭제됐으면 빈 Optional을 반환한다
    @Test
    @DisplayName("rotateRefreshToken — 사용자가 없으면 빈 Optional")
    void rotateRefreshToken_userMissing_returnsEmpty() {
        UUID userId = UUID.randomUUID();
        RefreshToken stored = RefreshToken.create(userId, "old-hash", Instant.now().plusSeconds(3600));

        when(jwtTokenProvider.hashRefreshToken("raw-old")).thenReturn("old-hash");
        when(refreshTokenRepository.findByTokenHash("old-hash")).thenReturn(Optional.of(stored));
        when(userRepository.findById(stored.getUserId())).thenReturn(Optional.empty());

        Optional<AuthTokenService.TokenPair> result = service.rotateRefreshToken("raw-old");

        assertThat(result).isEmpty();
        verify(refreshTokenRepository, never()).deleteByTokenHash(anyString());
    }

    // Refresh Token 쿠키 값이 있으면 해시로 변환 후 DB에서 삭제한다
    @Test
    @DisplayName("revokeRefreshToken — 토큰이 있으면 해시로 DB에서 삭제")
    void revokeRefreshToken_withToken_deletesFromDb() {
        when(jwtTokenProvider.hashRefreshToken("raw-token")).thenReturn("hashed");

        service.revokeRefreshToken("raw-token");

        verify(refreshTokenRepository).deleteByTokenHash("hashed");
    }

    // Refresh Token 쿠키가 없으면(null) DB 삭제를 시도하지 않는다
    @Test
    @DisplayName("revokeRefreshToken — 토큰이 null이면 DB 삭제 미호출")
    void revokeRefreshToken_null_noOp() {
        service.revokeRefreshToken(null);

        verify(refreshTokenRepository, never()).deleteByTokenHash(anyString());
        verifyNoInteractions(jwtTokenProvider);
    }

    // Access Token 발급은 JwtTokenProvider에 그대로 위임한다
    @Test
    @DisplayName("issueAccessToken — JwtTokenProvider에 위임")
    void issueAccessToken_delegatesToProvider() {
        UUID userId = UUID.randomUUID();
        when(jwtTokenProvider.generateToken(userId, "e@x.com", "USER")).thenReturn("issued-jwt");

        String token = service.issueAccessToken(userId, "e@x.com", "USER");

        assertThat(token).isEqualTo("issued-jwt");
    }
}
