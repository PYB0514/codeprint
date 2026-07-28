// STOMP 기반 WebSocket 설정
package com.codeprint.interfaces.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthorizationInterceptor authorizationInterceptor;
    private final WebSocketConnectionLimitInterceptor connectionLimitInterceptor;

    // /topic 메시지 브로커와 /app 목적지 prefix를 설정
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    // /ws 엔드포인트에 SockJS 폴백을 포함한 STOMP 연결 등록 — 핸드셰이크 시점 접속 IP를 세션 속성에 저장
    // (연결수 제한이 STOMP 레벨에서 이 IP를 읽어야 함, ClientIpHandshakeInterceptor 참조)
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("http://localhost:3000", "https://codeprint-iota.vercel.app")
                .addInterceptors(new ClientIpHandshakeInterceptor())
                .withSockJS();
    }

    // SUBSCRIBE 인가 검증 + CONNECT 연결수·SEND 빈도 제한 인터셉터 등록
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authorizationInterceptor, connectionLimitInterceptor);
    }
}
