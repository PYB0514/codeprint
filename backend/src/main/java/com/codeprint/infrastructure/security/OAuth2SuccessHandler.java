// GitHub OAuth2 로그인 성공 후 JWT를 HttpOnly 쿠키로 발급하는 핸들러
package com.codeprint.infrastructure.security;

import com.codeprint.application.user.UserCommandService;
import com.codeprint.domain.user.User;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserCommandService userCommandService;
    private final OAuth2AuthorizedClientService authorizedClientService;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    // OAuth2 인증 성공 시 JWT를 발급하고 프론트엔드로 리다이렉트
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        Map<String, Object> attributes = oAuth2User.getAttributes();

        Long githubId = ((Number) attributes.get("id")).longValue();
        String email = (String) attributes.get("email");
        String username = (String) attributes.get("login");

        // email이 null인 경우 (GitHub private email 설정) 대체값 사용
        if (email == null) {
            email = username + "@github.com";
        }

        User user = userCommandService.getOrCreateUser(githubId, email, username);

        // GitHub access token 추출 후 저장 (private 레포 API 접근용)
        if (authentication instanceof OAuth2AuthenticationToken oauthToken) {
            OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
                    oauthToken.getAuthorizedClientRegistrationId(), oauthToken.getName());
            if (client != null && client.getAccessToken() != null) {
                userCommandService.saveGithubAccessToken(user.getId(), client.getAccessToken().getTokenValue());
            }
        }

        String token = jwtTokenProvider.generateToken(user.getId(), user.getEmail(), user.getRole().name());
        log.info("OAuth2 login success: userId={}, username={}", user.getId(), username);

        // JWT를 HttpOnly 쿠키로 설정 — XSS로부터 토큰 보호
        Cookie jwtCookie = new Cookie("jwt", token);
        jwtCookie.setHttpOnly(true);
        jwtCookie.setPath("/");
        jwtCookie.setMaxAge(3600); // 1시간
        // 운영 환경(HTTPS)에서는 Secure 플래그 활성화
        boolean isSecure = !frontendUrl.startsWith("http://localhost");
        jwtCookie.setSecure(isSecure);
        response.addCookie(jwtCookie);

        // 토큰 없이 대시보드로 직접 리다이렉트
        getRedirectStrategy().sendRedirect(request, response, frontendUrl + "/dashboard");
    }
}
