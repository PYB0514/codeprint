// 인증 관련 REST API 컨트롤러
package com.codeprint.interfaces.api;

import com.codeprint.domain.user.RefreshToken;
import com.codeprint.domain.user.RefreshTokenRepository;
import com.codeprint.domain.user.User;
import com.codeprint.domain.user.UserRepository;
import com.codeprint.infrastructure.security.JwtTokenProvider;
import com.codeprint.infrastructure.storage.S3Service;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final S3Service s3Service;

    // Refresh Token 유효 기간: 7일
    private static final long REFRESH_TOKEN_EXPIRY_SECONDS = 7 * 24 * 60 * 60L;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    // 현재 인증된 사용자 정보를 반환
    @GetMapping("/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("id", user.getId());
        body.put("email", user.getEmail());
        body.put("username", user.getUsername());
        body.put("plan", user.getPlan());
        body.put("hasGithubToken", user.getGithubAccessToken() != null);
        body.put("avatarUrl", s3Service.toPresignedUrl(user.getAvatarUrl()));
        body.put("graphBgUrl", s3Service.toPresignedUrl(user.getGraphBgUrl()));
        return ResponseEntity.ok(body);
    }

    // Refresh Token으로 새 Access Token 발급
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(HttpServletRequest request, HttpServletResponse response) {
        String rawToken = extractCookie(request, "refresh_token");
        if (rawToken == null) {
            return ResponseEntity.status(401).body(Map.of("error", "No refresh token"));
        }

        String tokenHash = jwtTokenProvider.hashRefreshToken(rawToken);
        Optional<RefreshToken> storedOpt = refreshTokenRepository.findByTokenHash(tokenHash);
        if (storedOpt.isEmpty() || storedOpt.get().isExpired()) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid or expired refresh token"));
        }

        RefreshToken stored = storedOpt.get();
        return userRepository.findById(stored.getUserId()).map(user -> {
            // 기존 토큰 교체 (Refresh Token Rotation)
            refreshTokenRepository.deleteByTokenHash(tokenHash);
            String newRawToken = jwtTokenProvider.generateRefreshToken();
            String newHash = jwtTokenProvider.hashRefreshToken(newRawToken);
            refreshTokenRepository.save(RefreshToken.create(user.getId(), newHash,
                    Instant.now().plusSeconds(REFRESH_TOKEN_EXPIRY_SECONDS)));

            String newJwt = jwtTokenProvider.generateToken(user.getId(), user.getEmail(), user.getRole().name());
            boolean isSecure = !frontendUrl.startsWith("http://localhost");
            String sameSite = isSecure ? "None" : "Lax";

            ResponseCookie jwtCookie = ResponseCookie.from("jwt", newJwt)
                    .httpOnly(true)
                    .path("/")
                    .maxAge(3600)
                    .secure(isSecure)
                    .sameSite(sameSite)
                    .build();
            response.addHeader(HttpHeaders.SET_COOKIE, jwtCookie.toString());

            ResponseCookie refreshCookie = ResponseCookie.from("refresh_token", newRawToken)
                    .httpOnly(true)
                    .path("/api/auth")
                    .maxAge(REFRESH_TOKEN_EXPIRY_SECONDS)
                    .secure(isSecure)
                    .sameSite(sameSite)
                    .build();
            response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());

            return ResponseEntity.<Map<String, Object>>ok(Map.of("ok", true));
        }).orElseGet(() -> ResponseEntity.status(401).body(Map.of("error", "User not found")));
    }

    // jwt 쿠키 만료 + 세션 무효화로 로그아웃 처리
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        revokeRefreshToken(request);
        invalidateSession(request);
        expireJwtCookie(response);
        expireRefreshTokenCookie(response);
        return ResponseEntity.noContent().build();
    }

    // 쿠키 만료 + 세션 무효화 후 프론트엔드로 리다이렉트
    @GetMapping("/logout-redirect")
    public void logoutRedirect(HttpServletRequest request, HttpServletResponse response) throws java.io.IOException {
        revokeRefreshToken(request);
        invalidateSession(request);
        expireJwtCookie(response);
        expireRefreshTokenCookie(response);
        response.sendRedirect(frontendUrl);
    }

    // DB에서 Refresh Token 무효화
    private void revokeRefreshToken(HttpServletRequest request) {
        String rawToken = extractCookie(request, "refresh_token");
        if (rawToken != null) {
            refreshTokenRepository.deleteByTokenHash(jwtTokenProvider.hashRefreshToken(rawToken));
        }
    }

    // Spring Security OAuth 세션 무효화
    private void invalidateSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    // jwt 쿠키 만료 공통 처리
    private void expireJwtCookie(HttpServletResponse response) {
        boolean isSecure = !frontendUrl.startsWith("http://localhost");
        ResponseCookie expiredCookie = ResponseCookie.from("jwt", "")
                .httpOnly(true)
                .path("/")
                .maxAge(0)
                .secure(isSecure)
                .sameSite(isSecure ? "None" : "Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, expiredCookie.toString());
    }

    // refresh_token 쿠키 만료 처리
    private void expireRefreshTokenCookie(HttpServletResponse response) {
        boolean isSecure = !frontendUrl.startsWith("http://localhost");
        ResponseCookie expiredCookie = ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .path("/api/auth")
                .maxAge(0)
                .secure(isSecure)
                .sameSite(isSecure ? "None" : "Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, expiredCookie.toString());
    }

    // 요청 쿠키에서 특정 이름의 값 추출
    private String extractCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }
}
