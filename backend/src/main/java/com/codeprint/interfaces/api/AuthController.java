// 인증 관련 REST API 컨트롤러
package com.codeprint.interfaces.api;

import com.codeprint.domain.user.User;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    // 현재 인증된 사용자 정보를 반환
    @GetMapping("/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        return ResponseEntity.ok(Map.of(
                "id", user.getId(),
                "email", user.getEmail(),
                "username", user.getUsername(),
                "plan", user.getPlan(),
                "hasGithubToken", user.getGithubAccessToken() != null
        ));
    }

    // jwt 쿠키를 만료시켜 로그아웃 처리
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        expireJwtCookie(response);
        return ResponseEntity.noContent().build();
    }

    // 쿠키 만료 후 프론트엔드로 리다이렉트 — 프록시 우회용 직접 호출 전용
    @GetMapping("/logout-redirect")
    public void logoutRedirect(HttpServletResponse response) throws java.io.IOException {
        expireJwtCookie(response);
        response.sendRedirect(frontendUrl);
    }

    // jwt 쿠키 만료 공통 처리
    private void expireJwtCookie(HttpServletResponse response) {
        Cookie expiredCookie = new Cookie("jwt", "");
        expiredCookie.setHttpOnly(true);
        expiredCookie.setPath("/");
        expiredCookie.setMaxAge(0);
        response.addCookie(expiredCookie);
    }
}
