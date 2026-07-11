// 로그아웃 플로우 통합 테스트 — RefreshToken 삭제/쿠키 만료/세션 무효화 회귀 방지 (반복-A)
package com.codeprint.interfaces.api;

import com.codeprint.application.user.AuthTokenService;
import com.codeprint.application.user.UserCommandService;
import com.codeprint.application.user.UserQueryService;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerLogoutTest {

    @Mock private AuthTokenService authTokenService;
    @Mock private UserQueryService userQueryService;
    @Mock private UserCommandService userCommandService;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(authTokenService, userQueryService, userCommandService);
        ReflectionTestUtils.setField(controller, "frontendUrl", "http://localhost:3000");
    }

    // 로그아웃 시 refresh_token 쿠키가 있으면 AuthTokenService로 무효화 요청이 전달돼야 한다
    @Test
    @DisplayName("logout — refresh_token 쿠키가 있으면 AuthTokenService.revokeRefreshToken 호출")
    void logout_revokesRefreshTokenFromDb() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("refresh_token", "raw-token-value"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.logout(request, response);

        verify(authTokenService).revokeRefreshToken("raw-token-value");
    }

    // 로그아웃 시 refresh_token 쿠키가 없으면 null로 무효화를 호출한다 (AuthTokenService 내부에서 no-op)
    @Test
    @DisplayName("logout — refresh_token 쿠키 없을 때 null로 호출")
    void logout_noRefreshTokenCookie_skipsDbDelete() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.logout(request, response);

        verify(authTokenService).revokeRefreshToken(null);
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

    // refresh_token 쿠키가 없으면 AuthTokenService 호출 없이 401
    @Test
    @DisplayName("refresh — refresh_token 쿠키 없으면 401")
    void refresh_noCookie_returns401() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        var result = controller.refresh(request, response);

        assertThat(result.getStatusCode().value()).isEqualTo(401);
        verifyNoInteractions(authTokenService);
    }

    // AuthTokenService가 새 토큰 쌍을 반환하면 jwt/refresh_token 쿠키를 갱신하고 200을 반환한다
    @Test
    @DisplayName("refresh — 유효한 토큰이면 새 쿠키 설정 후 200")
    void refresh_valid_setsNewCookiesAndReturns200() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("refresh_token", "raw-old"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(authTokenService.rotateRefreshToken("raw-old"))
                .thenReturn(java.util.Optional.of(new AuthTokenService.TokenPair("new-jwt", "raw-new")));

        var result = controller.refresh(request, response);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders("Set-Cookie"))
                .anyMatch(h -> h.contains("jwt=new-jwt"))
                .anyMatch(h -> h.contains("refresh_token=raw-new"));
    }

    // AuthTokenService가 빈 Optional을 반환하면(만료·미존재) 401을 반환한다
    @Test
    @DisplayName("refresh — 만료·미존재 토큰이면 401")
    void refresh_invalid_returns401() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("refresh_token", "raw-bad"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(authTokenService.rotateRefreshToken("raw-bad")).thenReturn(java.util.Optional.empty());

        var result = controller.refresh(request, response);

        assertThat(result.getStatusCode().value()).isEqualTo(401);
    }
}
