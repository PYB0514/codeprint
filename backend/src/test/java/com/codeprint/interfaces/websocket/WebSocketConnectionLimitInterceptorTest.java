// WebSocketConnectionLimitInterceptor 단위 테스트 — IP당 동시 연결 상한, 세션당 SEND 빈도 상한, DISCONNECT 회수 회귀 방지
package com.codeprint.interfaces.websocket;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebSocketConnectionLimitInterceptorTest {

    private final WebSocketConnectionLimitInterceptor interceptor = new WebSocketConnectionLimitInterceptor();

    private Message<byte[]> connectMessage(String ip, String sessionId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId(sessionId);
        Map<String, Object> sessionAttrs = new HashMap<>();
        sessionAttrs.put(ClientIpHandshakeInterceptor.SESSION_ATTR_CLIENT_IP, ip);
        accessor.setSessionAttributes(sessionAttrs);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> sendMessage(String sessionId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setSessionId(sessionId);
        accessor.setDestination("/app/collab/x/cursor");
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    @DisplayName("같은 IP에서 30개 연결까지는 허용, 31번째부터 거부")
    void connectLimit_perIp_30allowed_31stRejected() {
        String ip = "1.1.1.1";
        for (int i = 0; i < 30; i++) {
            String sid = "sid-" + i;
            assertThatCode(() -> interceptor.preSend(connectMessage(ip, sid), null)).doesNotThrowAnyException();
        }

        assertThatThrownBy(() -> interceptor.preSend(connectMessage(ip, "sid-30"), null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("DISCONNECT 시 연결 수를 회수해 다시 연결 가능")
    void disconnect_releasesSlot() {
        String ip = "2.2.2.2";
        for (int i = 0; i < 30; i++) {
            interceptor.preSend(connectMessage(ip, "sid-" + i), null);
        }
        // 31번째는 아직 거부됨
        assertThatThrownBy(() -> interceptor.preSend(connectMessage(ip, "sid-30"), null))
                .isInstanceOf(IllegalStateException.class);

        // 세션 하나가 끊기면 슬롯 회수
        StompHeaderAccessor disconnectAccessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        disconnectAccessor.setSessionId("sid-0");
        Map<String, Object> sessionAttrs = new HashMap<>();
        sessionAttrs.put(ClientIpHandshakeInterceptor.SESSION_ATTR_CLIENT_IP, ip);
        disconnectAccessor.setSessionAttributes(sessionAttrs);
        disconnectAccessor.setLeaveMutable(true);
        Message<byte[]> disconnectMessage = MessageBuilder.createMessage(new byte[0], disconnectAccessor.getMessageHeaders());
        interceptor.onApplicationEvent(new SessionDisconnectEvent(this, disconnectMessage, "sid-0", null));

        assertThatCode(() -> interceptor.preSend(connectMessage(ip, "sid-30"), null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("다른 IP는 서로 다른 카운터 — 한쪽 소진이 다른 쪽에 영향 없음")
    void connectLimit_separatePerIp() {
        for (int i = 0; i < 30; i++) {
            interceptor.preSend(connectMessage("3.3.3.3", "a-" + i), null);
        }
        assertThatThrownBy(() -> interceptor.preSend(connectMessage("3.3.3.3", "a-30"), null))
                .isInstanceOf(IllegalStateException.class);

        assertThatCode(() -> interceptor.preSend(connectMessage("4.4.4.4", "b-0"), null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("같은 세션에서 40개 SEND까지는 허용, 41번째부터 거부")
    void sendRateLimit_perSession_40allowed_41stRejected() {
        String sessionId = "session-x";
        for (int i = 0; i < 40; i++) {
            assertThatCode(() -> interceptor.preSend(sendMessage(sessionId), null)).doesNotThrowAnyException();
        }

        assertThatThrownBy(() -> interceptor.preSend(sendMessage(sessionId), null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("SEND 빈도 제한은 세션별로 분리 — 한 세션 소진이 다른 세션에 영향 없음")
    void sendRateLimit_separatePerSession() {
        for (int i = 0; i < 40; i++) {
            interceptor.preSend(sendMessage("session-a"), null);
        }
        assertThatThrownBy(() -> interceptor.preSend(sendMessage("session-a"), null))
                .isInstanceOf(IllegalStateException.class);

        assertThatCode(() -> interceptor.preSend(sendMessage("session-b"), null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("SUBSCRIBE 등 CONNECT/SEND가 아닌 명령은 검사 없이 통과")
    void otherCommands_passThrough() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/analysis/x");
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatCode(() -> interceptor.preSend(message, null)).doesNotThrowAnyException();
    }
}
