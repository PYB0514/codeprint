// IP 기반 API 요청 제한 필터 — 쓰기 엔드포인트 남용 방어
package com.codeprint.infrastructure.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
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
import java.util.concurrent.TimeUnit;

@Component
@Order(2)
public class RateLimitFilter implements Filter {

    private final PathMatcher pathMatcher = new AntPathMatcher();

    // IP+카테고리별 버킷 — 가장 긴 집계 창(현재 3분)보다 넉넉한 TTL로 유휴 항목을 자동 정리해
    // 무제한 증가(스푸핑 IP를 계속 바꿔가며 항목을 무한 생성하는 2차 DoS)를 막는다. maximumSize는
    // TTL 만료 전에도 상한을 보장하는 2중 방어(Context103 MEDIUM 발견 중 미해결로 남았던 부분 수정)
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .maximumSize(100_000)
            .build();

    // 레이트리밋 대상 — 메서드·경로 패턴·카테고리·허용 횟수·집계 창(분)
    private record RateLimitRule(String method, String pathPattern, String category, int limit, int windowMinutes) {}

    // 남용 위험이 있는 쓰기 엔드포인트 전체 등록 (신규 추가 시 이 목록에만 추가하면 됨)
    // ★analysis는 레포 클론+정적분석으로 다른 카테고리보다 비용이 훨씬 커, 단순 쓰기(post-create 등)보다
    // 오히려 한도가 널널했던 걸 3분당 1회로 교정(2026-07-12) — decisions/DECISIONS_BACKEND.md 참조
    private final List<RateLimitRule> rules = List.of(
            new RateLimitRule("POST", "/api/analyses", "analysis", 1, 3),          // 레포 클론+정적분석 비용 큼
            new RateLimitRule("POST", "/api/attachments/presign", "attach", 20, 1), // S3 비용
            new RateLimitRule("POST", "/api/community/posts", "post-create", 5, 1),
            new RateLimitRule("POST", "/api/community/posts/*/like", "post-like", 60, 1),
            new RateLimitRule("POST", "/api/graphs/*/nodes/*/comments", "comment-create", 20, 1),
            new RateLimitRule("POST", "/api/feedback", "feedback", 5, 1),
            new RateLimitRule("POST", "/api/reports", "report", 5, 1),
            new RateLimitRule("POST", "/api/messages/*", "message-send", 30, 1),
            new RateLimitRule("POST", "/api/users/*/follow", "follow", 30, 1),
            new RateLimitRule("POST", "/api/push/subscribe", "push-subscribe", 10, 1),
            // 시크릿 인증이 1차 방어선이나, 유출 시에도 남용 폭을 제한하는 2차 방어(독립 보안검증 권고, PR #569)
            new RateLimitRule("POST", "/api/cron/refresh-featured", "cron-refresh", 2, 60), // 레포 최대 5개 클론+분석
            new RateLimitRule("POST", "/api/cron/daily-digest", "cron-digest", 5, 60)
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

    // 집계 창(분) 기준 한도로 새 버킷 생성
    private Bucket newBucket(int limit, int windowMinutes) {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(limit, Refill.intervally(limit, Duration.ofMinutes(windowMinutes))))
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
            Bucket bucket = buckets.get(key, k -> newBucket(matched.limit(), matched.windowMinutes()));
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
