// IP 기반 API 요청 제한 필터 — 분석/첨부 엔드포인트 남용 방어
package com.codeprint.infrastructure.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Order(2)
public class RateLimitFilter implements Filter {

    // 분석 시작은 레포 클론 비용이 크므로 IP당 10회/분으로 제한
    private final Map<String, Bucket> analysisBuckets = new ConcurrentHashMap<>();
    // 첨부 presign은 S3 비용이 있으므로 IP당 20회/분으로 제한
    private final Map<String, Bucket> attachBuckets = new ConcurrentHashMap<>();

    // 분석 API 버킷 — 1분마다 10회 충전
    private Bucket newAnalysisBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(10, Refill.intervally(10, Duration.ofMinutes(1))))
                .build();
    }

    // 첨부 API 버킷 — 1분마다 20회 충전
    private Bucket newAttachBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(20, Refill.intervally(20, Duration.ofMinutes(1))))
                .build();
    }

    // 요청 IP 추출 — Railway 프록시 환경에서 X-Forwarded-For 우선
    private String extractIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    // 엔드포인트별 버킷 선택 후 초과 시 429 반환
    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        String method = request.getMethod();
        String path = request.getRequestURI();
        String ip = extractIp(request);

        Bucket bucket = null;

        if ("POST".equals(method) && path.startsWith("/api/analyses")) {
            bucket = analysisBuckets.computeIfAbsent(ip, k -> newAnalysisBucket());
        } else if ("POST".equals(method) && path.startsWith("/api/attachments/presign")) {
            bucket = attachBuckets.computeIfAbsent(ip, k -> newAttachBucket());
        }

        if (bucket != null && !bucket.tryConsume(1)) {
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"요청이 너무 많습니다. 잠시 후 다시 시도해주세요.\"}");
            return;
        }

        chain.doFilter(request, response);
    }
}
