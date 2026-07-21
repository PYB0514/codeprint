// RateLimitFilter 단위 테스트 — XFF 스푸핑 우회·규칙 매칭·한도 초과 회귀 방지
package com.codeprint.infrastructure.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.PrintWriter;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain chain;
    @Mock private PrintWriter writer;

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() throws Exception {
        filter = new RateLimitFilter();
        lenient().when(response.getWriter()).thenReturn(writer);
    }

    @Test
    @DisplayName("레이트리밋 대상 아닌 경로는 그대로 통과")
    void nonMatchingPath_passesThrough() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/community/posts");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(429);
    }

    @Test
    @DisplayName("한도 초과 시 429 반환하고 체인 진행 안 함")
    void limitExceeded_returns429() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/feedback"); // 분당 5회 한도
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("1.1.1.1");

        for (int i = 0; i < 5; i++) {
            filter.doFilter(request, response, chain);
        }
        verify(chain, times(5)).doFilter(request, response);

        filter.doFilter(request, response, chain); // 6번째 — 한도 초과

        verify(response).setStatus(429);
        verify(chain, times(5)).doFilter(request, response); // 6번째는 체인 미진행
    }

    @Test
    @DisplayName("XFF 스푸핑 우회 차단 — 클라이언트가 앞에 위조 IP를 붙여도 실제 접속 IP(마지막 값)로 버킷 구분")
    void xffSpoofing_usesLastHopNotClientProvidedFirstHop() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/feedback");
        // 공격자가 매 요청마다 다른 위조 IP를 앞에 붙여도, 실제 접속 IP(Railway가 추가한 마지막 값)는 동일
        when(request.getHeader("X-Forwarded-For"))
                .thenReturn("9.9.9.1, 2.2.2.2")
                .thenReturn("9.9.9.2, 2.2.2.2")
                .thenReturn("9.9.9.3, 2.2.2.2")
                .thenReturn("9.9.9.4, 2.2.2.2")
                .thenReturn("9.9.9.5, 2.2.2.2")
                .thenReturn("9.9.9.6, 2.2.2.2"); // 6번째 — 실제 IP(2.2.2.2) 기준으로는 한도 초과

        for (int i = 0; i < 5; i++) {
            filter.doFilter(request, response, chain);
        }
        filter.doFilter(request, response, chain); // 6번째, 위조된 첫 hop이 매번 달라도 차단돼야 함

        verify(response).setStatus(429);
    }

    @Test
    @DisplayName("analysis 카테고리는 3분당 1회 — 클론+정적분석 비용이 커 다른 쓰기 API보다 엄격")
    void analysisCategory_limitedToOnePerThreeMinutes() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/analyses");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("4.4.4.4");

        filter.doFilter(request, response, chain);
        verify(chain, times(1)).doFilter(request, response);

        filter.doFilter(request, response, chain); // 같은 3분 창 내 2번째 — 초과
        verify(response).setStatus(429);
        verify(chain, times(1)).doFilter(request, response);
    }

    @Test
    @DisplayName("카테고리가 다르면 서로 다른 버킷 — 한 카테고리 소진이 다른 카테고리에 영향 없음")
    void differentCategories_useSeparateBuckets() throws Exception {
        when(request.getRemoteAddr()).thenReturn("3.3.3.3");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);

        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/feedback");
        for (int i = 0; i < 5; i++) {
            filter.doFilter(request, response, chain);
        }
        filter.doFilter(request, response, chain);
        verify(response).setStatus(429); // feedback 버킷 소진

        when(request.getRequestURI()).thenReturn("/api/community/posts");
        filter.doFilter(request, response, chain); // post-create 버킷은 별도라 통과
        verify(chain, times(6)).doFilter(request, response); // 5(feedback 성공) + 1(post-create 성공)
    }

    @Test
    @DisplayName("cron-refresh 카테고리는 시간당 2회 — 시크릿 유출 시에도 남용 폭 제한(PR #569 보안검증 권고)")
    void cronRefreshCategory_limitedToTwoPerHour() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/cron/refresh-featured");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("5.5.5.5");

        filter.doFilter(request, response, chain);
        filter.doFilter(request, response, chain);
        verify(chain, times(2)).doFilter(request, response);

        filter.doFilter(request, response, chain); // 3번째 — 초과
        verify(response).setStatus(429);
        verify(chain, times(2)).doFilter(request, response);
    }

    @Test
    @DisplayName("report-fp 카테고리는 분당 10회 — 오탐 신고 학습 신호(fp_reports) 스팸 오염 방지(R22)")
    void reportFpCategory_limitedToTenPerMinute() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/projects/abc-123/warnings/report-fp");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("6.6.6.6");

        for (int i = 0; i < 10; i++) {
            filter.doFilter(request, response, chain);
        }
        verify(chain, times(10)).doFilter(request, response);

        filter.doFilter(request, response, chain); // 11번째 — 초과
        verify(response).setStatus(429);
        verify(chain, times(10)).doFilter(request, response);
    }

    @Test
    @DisplayName("커뮤니티 게시글 댓글은 분당 20회 — 노드 댓글과 별도 버킷(R22)")
    void postCommentCategory_limitedToTwentyPerMinute() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/community/posts/abc-123/comments");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("7.7.7.7");

        for (int i = 0; i < 20; i++) {
            filter.doFilter(request, response, chain);
        }
        verify(chain, times(20)).doFilter(request, response);

        filter.doFilter(request, response, chain); // 21번째 — 초과
        verify(response).setStatus(429);
        verify(chain, times(20)).doFilter(request, response);
    }

    @Test
    @DisplayName("결제 prepare 3종은 같은 payment-prepare 버킷 공유 — 엔드포인트를 바꿔가며 우회 불가(R22)")
    void paymentPrepareEndpoints_shareSameBucket() throws Exception {
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("8.8.8.8");
        when(request.getMethod()).thenReturn("POST");

        when(request.getRequestURI()).thenReturn("/api/payments/toss/prepare");
        for (int i = 0; i < 3; i++) {
            filter.doFilter(request, response, chain);
        }
        when(request.getRequestURI()).thenReturn("/api/teams/payment/prepare");
        filter.doFilter(request, response, chain);
        when(request.getRequestURI()).thenReturn("/api/teams/xyz-1/seats/payment/prepare");
        filter.doFilter(request, response, chain);
        verify(chain, times(5)).doFilter(request, response); // 3종 합쳐 5회까지는 통과

        filter.doFilter(request, response, chain); // 6번째 — 엔드포인트 전환해도 같은 버킷이라 초과
        verify(response).setStatus(429);
        verify(chain, times(5)).doFilter(request, response);
    }
}
