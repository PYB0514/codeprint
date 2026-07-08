// CsrfHeaderFilter 단위 테스트 — 커스텀 헤더 요구·예외 경로·메서드 범위 회귀 방지
package com.codeprint.infrastructure.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.PrintWriter;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CsrfHeaderFilterTest {

    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain chain;
    @Mock private PrintWriter writer;

    private CsrfHeaderFilter filter;

    @BeforeEach
    void setUp() throws Exception {
        filter = new CsrfHeaderFilter();
        lenient().when(response.getWriter()).thenReturn(writer);
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "DELETE", "PATCH"})
    @DisplayName("상태변경 메서드 + 헤더 없음 → 403, 체인 진행 안 함")
    void stateChangingMethod_withoutHeader_rejected(String method) throws Exception {
        when(request.getMethod()).thenReturn(method);
        when(request.getRequestURI()).thenReturn("/api/community/posts");
        when(request.getHeader("X-Requested-With")).thenReturn(null);

        filter.doFilter(request, response, chain);

        verify(response).setStatus(403);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("상태변경 메서드 + 올바른 헤더 → 통과")
    void stateChangingMethod_withHeader_passes() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/community/posts");
        when(request.getHeader("X-Requested-With")).thenReturn("XMLHttpRequest");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(403);
    }

    @Test
    @DisplayName("GET은 헤더 없어도 통과")
    void getMethod_withoutHeader_passes() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/community/posts");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(403);
    }

    @Test
    @DisplayName("웹훅 경로는 헤더 없어도 통과 (서버-투-서버, 커스텀 헤더 강제 불가)")
    void webhookPath_withoutHeader_passes() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/webhooks/github");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(403);
    }

    @Test
    @DisplayName("mcp/rpc 경로는 헤더 없어도 통과 (외부 MCP 클라이언트, 브라우저 쿠키 플로우 아님)")
    void mcpRpcPath_withoutHeader_passes() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/mcp/rpc");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(403);
    }
}
