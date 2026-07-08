// IP 기반 API 요청 제한 필터 — 쓰기 엔드포인트 남용 방어
package com.codeprint.infrastructure.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Order(2)
public class RateLimitFilter implements Filter {

    private final PathMatcher pathMatcher = new AntPathMatcher();

    // IP+카테고리별 버킷 (카테고리마다 한도가 달라 규칙에서 한도를 받아 생성)
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    // 레이트리밋 대상 — 메서드·경로 패턴·카테고리·분당 허용 횟수
    private record RateLimitRule(String method, String pathPattern, String category, int limitPerMinute) {}

    // 남용 위험이 있는 쓰기 엔드포인트 전체 등록 (신규 추가 시 이 목록에만 추가하면 됨)
    private final List<RateLimitRule> rules = List.of(
            new RateLimitRule("POST", "/api/analyses", "analysis", 10),          // 레포 클론 비용 큼
            new RateLimitRule("POST", "/api/attachments/presign", "attach", 20), // S3 비용
            new RateLimitRule("POST", "/api/community/posts", "post-create", 5),
            new RateLimitRule("POST", "/api/community/posts/*/like", "post-like", 60),
            new RateLimitRule("POST", "/api/graphs/*/nodes/*/comments", "comment-create", 20),
            new RateLimitRule("POST", "/api/feedback", "feedback", 5),
            new RateLimitRule("POST", "/api/messages/*", "message-send", 30),
            new RateLimitRule("POST", "/api/users/*/follow", "follow", 30),
            new RateLimitRule("POST", "/api/push/subscribe", "push-subscribe", 10),
            new RateLimitRule("POST", "/mcp/rpc", "mcp-rpc", 30)
    );

    // 요청 IP 추출 — Railway 프록시가 실제 접속 IP를 X-Forwarded-For 맨 끝에 추가하므로 마지막 값을 사용
    // (맨 앞 값은 클라이언트가 임의로 넣을 수 있어 위조 가능 — 이전 구현의 XFF 스푸핑 우회 결함)
    private String extractIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] parts = forwarded.split(",");
            return parts[parts.length - 1].trim();
        }
        return request.getRemoteAddr();
    }

    // 분당 한도로 새 버킷 생성
    private Bucket newBucket(int limitPerMinute) {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(limitPerMinute, Refill.intervally(limitPerMinute, Duration.ofMinutes(1))))
                .build();
    }

    // 요청 메서드·경로에 맞는 규칙 탐색
    private RateLimitRule matchRule(String method, String path) {
        return rules.stream()
                .filter(r -> r.method().equals(method) && pathMatcher.match(r.pathPattern(), path))
                .findFirst()
                .orElse(null);
    }

    // 매칭된 규칙의 버킷 소진 시 429 반환
    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        RateLimitRule matched = matchRule(request.getMethod(), request.getRequestURI());

        if (matched != null) {
            String key = extractIp(request) + ":" + matched.category();
            Bucket bucket = buckets.computeIfAbsent(key, k -> newBucket(matched.limitPerMinute()));
            if (!bucket.tryConsume(1)) {
                response.setStatus(429);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"error\":\"요청이 너무 많습니다. 잠시 후 다시 시도해주세요.\"}");
                return;
            }
        }

        chain.doFilter(request, response);
    }
}
