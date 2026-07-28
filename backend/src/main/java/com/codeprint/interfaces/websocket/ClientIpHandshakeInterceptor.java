// WebSocket 핸드셰이크 시점의 실제 접속 IP를 세션 속성에 저장 — STOMP 레벨(ChannelInterceptor)에선
// HttpServletRequest에 접근할 수 없어, HTTP 단계에서 한 번 추출해 넘겨줘야 함
package com.codeprint.interfaces.websocket;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

public class ClientIpHandshakeInterceptor implements HandshakeInterceptor {

    static final String SESSION_ATTR_CLIENT_IP = "clientIp";

    // Railway 프록시가 실제 접속 IP를 X-Forwarded-For 맨 끝에 추가하므로 마지막 값을 사용
    // (RateLimitFilter.extractIp와 동일 원칙 — 여긴 HttpServletRequest가 아닌 ServerHttpRequest라 별도 구현)
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                    WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String ip = request.getRemoteAddress() != null ? request.getRemoteAddress().getAddress().getHostAddress() : "unknown";
        if (request instanceof ServletServerHttpRequest servletRequest) {
            String forwarded = servletRequest.getServletRequest().getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                String[] parts = forwarded.split(",");
                ip = parts[parts.length - 1].trim();
            }
        }
        attributes.put(SESSION_ATTR_CLIENT_IP, ip);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                WebSocketHandler wsHandler, Exception exception) {
    }
}
