// 모든 응답에 보안 헤더를 추가하는 서블릿 필터
package com.codeprint.infrastructure.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(1)
public class SecurityHeadersFilter implements Filter {

    // 응답에 보안 헤더 삽입 후 다음 필터로 전달
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletResponse res = (HttpServletResponse) response;

        // 클릭재킹 방어
        res.setHeader("X-Frame-Options", "DENY");
        // MIME 타입 스니핑 방어
        res.setHeader("X-Content-Type-Options", "nosniff");
        // HTTPS 강제 (운영 환경)
        res.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        // 리퍼러 헤더로 민감 경로 노출 방지
        res.setHeader("Referrer-Policy", "no-referrer");
        // 불필요한 브라우저 기능 비활성화
        res.setHeader("Permissions-Policy", "geolocation=(), microphone=(), camera=()");
        // XSS 방어 — script-src는 React 빌드 인라인 스크립트 허용
        res.setHeader("Content-Security-Policy",
                "default-src 'self'; " +
                "script-src 'self' 'unsafe-inline'; " +
                "style-src 'self' 'unsafe-inline'; " +
                "img-src 'self' data: https:; " +
                "connect-src 'self' https://api.github.com https://*.sentry.io; " +
                "frame-ancestors 'none'");

        chain.doFilter(request, response);
    }
}
