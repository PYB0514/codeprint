// CSRF 심층방어 — 쿠키 인증(JWT, SameSite=None) 상태변경 요청에 커스텀 헤더 요구
package com.codeprint.infrastructure.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;

import java.io.IOException;
import java.util.List;
import java.util.Set;

// SameSite=None 쿠키는 cross-site 요청에도 자동 첨부되므로, CORS만으로는
// 단순 요청(form 등) 기반 CSRF를 막지 못한다. 브라우저는 커스텀 헤더를 cross-site로
// 보내려면 반드시 CORS preflight를 거치게 하므로, 이 헤더 요구가 곧 origin 화이트리스트 강제가 된다.
@Component
@Order(0)
public class CsrfHeaderFilter implements Filter {

    private static final String CSRF_HEADER = "X-Requested-With";
    private static final String CSRF_HEADER_VALUE = "XMLHttpRequest";
    private static final Set<String> PROTECTED_METHODS = Set.of("POST", "PUT", "DELETE", "PATCH");

    private final PathMatcher pathMatcher = new AntPathMatcher();

    // 브라우저 XHR/fetch가 아닌 서버-투-서버·리다이렉트 플로우는 커스텀 헤더를 보낼 수 없어 제외
    private final List<String> exemptPatterns = List.of(
            "/api/payments/webhook",
            "/api/webhooks/github",
            "/oauth2/**",
            "/login/**",
            "/mcp/**",
            "/api/dev/**",
            "/api/cron/**"
    );

    private boolean isExempt(String path) {
        return exemptPatterns.stream().anyMatch(p -> pathMatcher.match(p, path));
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        String method = request.getMethod();
        String path = request.getRequestURI();

        if (PROTECTED_METHODS.contains(method) && !isExempt(path)
                && !CSRF_HEADER_VALUE.equals(request.getHeader(CSRF_HEADER))) {
            response.setStatus(403);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"CSRF 방어 헤더가 없습니다.\"}");
            return;
        }

        chain.doFilter(request, response);
    }
}
