// 로그아웃 플로우 통합 테스트 — RefreshToken 삭제/쿠키 만료/세션 무효화 회귀 방지 (반복-A)
package com.codeprint.interfaces.api;

import com.codeprint.application.user.UserCommandService;
import com.codeprint.domain.user.RefreshTokenRepository;
import com.codeprint.domain.user.UserRepository;
import com.codeprint.infrastructure.security.JwtTokenProvider;
import com.codeprint.infrastructure.storage.S3Service;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerLogoutTest {

    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private UserRepository userRepository;
    @Mock private S3Service s3Service;
    @Mock private UserCommandService userCommandService;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(jwtTokenProvider, refreshTokenRepository, userRepository, s3Service, userCommandService);
        ReflectionTestUtils.setField(controller, "frontendUrl", "http://localhost:3000");
    }

    // 로그아웃 시 refresh_token 쿠키가 있으면 DB에서 토큰이 삭제돼야 한다
    @Test
    @DisplayName("logout — refresh_token 쿠키가 있으면 DB에서 토큰 삭제")
    void logout_revokesRefreshTokenFromDb() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("refresh_token", "raw-token-value"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtTokenProvider.hashRefreshToken("raw-token-value")).thenReturn("hashed-token");

        controller.logout(request, response);

        verify(refreshTokenRepository).deleteByTokenHash("hashed-token");
    }

    // 로그아웃 시 refresh_token 쿠키가 없으면 DB 삭제를 시도하지 않는다
    @Test
    @DisplayName("logout — refresh_token 쿠키 없을 때 DB 삭제 미호출")
    void logout_noRefreshTokenCookie_skipsDbDelete() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.logout(request, response);

        verify(refreshTokenRepository, never()).deleteByTokenHash(anyString());
    }

    // 로그아웃 후 jwt 쿠키가 maxAge=0으로 만료 헤더로 설정돼야 한다 (로컬: SameSite=Lax)
    @Test
    @DisplayName("logout — jwt 쿠키가 만료(maxAge=0) Set-Cookie 헤더로 설정된다")
    void logout_expiresJwtCookieInResponse() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.logout(request, response);

        String setCookieHeader = response.getHeader("Set-Cookie");
        assertThat(setCookieHeader).isNotNull();
        // jwt 또는 refresh_token 중 하나 이상 포함
        assertThat(response.getHeaders("Set-Cookie"))
                .anyMatch(h -> h.contains("Max-Age=0"))
                .anyMatch(h -> h.contains("jwt=") || h.contains("refresh_token="));
    }

    // 로그아웃 후 기존 세션이 무효화돼야 한다
    @Test
    @DisplayName("logout — 활성 세션이 있으면 무효화된다")
    void logout_invalidatesActiveSession() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        // 세션을 명시적으로 생성하면 getSession(false)로 반환됨
        HttpSession session = request.getSession(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.logout(request, response);

        // 세션이 무효화됐으므로 getAttribute 호출 시 IllegalStateException 발생
        boolean sessionInvalidated;
        try {
            session.getAttribute("any");
            sessionInvalidated = false;
        } catch (IllegalStateException e) {
            sessionInvalidated = true;
        }
        assertThat(sessionInvalidated).isTrue();
    }

    // 로그아웃 응답은 204 No Content여야 한다
    @Test
    @DisplayName("logout — 204 No Content 응답 반환")
    void logout_returns204() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        var result = controller.logout(request, response);

        assertThat(result.getStatusCode().value()).isEqualTo(204);
    }
}
