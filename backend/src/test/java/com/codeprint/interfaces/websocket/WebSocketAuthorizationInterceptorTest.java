// WebSocketAuthorizationInterceptor 단위 테스트 — 팀채팅/협업세션 구독 인가 회귀 방지(익명·비참가자 도청 차단)
package com.codeprint.interfaces.websocket;

import com.codeprint.application.collaboration.CollaborationApplicationService;
import com.codeprint.application.graph.GraphFacade;
import com.codeprint.domain.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.security.Principal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebSocketAuthorizationInterceptorTest {

    @Mock private GraphFacade graphFacade;
    @Mock private CollaborationApplicationService collaborationApplicationService;

    private WebSocketAuthorizationInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new WebSocketAuthorizationInterceptor(graphFacade, collaborationApplicationService);
    }

    private Message<byte[]> subscribeMessage(String destination, Principal user) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        if (user != null) accessor.setUser(user);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Principal principalOf(UUID userId) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        return new UsernamePasswordAuthenticationToken(user, null);
    }

    @Test
    @DisplayName("팀채팅 구독 — 미인증 사용자는 거부")
    void teamChatSubscribe_anonymous_rejected() {
        Message<byte[]> message = subscribeMessage("/topic/team/" + UUID.randomUUID() + "/chat", null);

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(graphFacade);
    }

    @Test
    @DisplayName("팀채팅 구독 — 그래프 접근 권한이 있으면 통과")
    void teamChatSubscribe_authorized_passes() {
        UUID graphId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Message<byte[]> message = subscribeMessage("/topic/team/" + graphId + "/chat", principalOf(userId));

        assertThatCode(() -> interceptor.preSend(message, null)).doesNotThrowAnyException();
        verify(graphFacade).verifyGraphOwnership(graphId, userId);
    }

    @Test
    @DisplayName("팀채팅 구독 — 그래프 접근 권한이 없으면 거부")
    void teamChatSubscribe_notAuthorized_rejected() {
        UUID graphId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        doThrow(new IllegalStateException("Not authorized to access this project"))
                .when(graphFacade).verifyGraphOwnership(graphId, userId);
        Message<byte[]> message = subscribeMessage("/topic/team/" + graphId + "/chat", principalOf(userId));

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("협업세션 구독 — 미인증 사용자는 거부")
    void collabSubscribe_anonymous_rejected() {
        Message<byte[]> message = subscribeMessage("/topic/collab/" + UUID.randomUUID(), null);

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(collaborationApplicationService);
    }

    @Test
    @DisplayName("협업세션 구독 — 참가자면 통과")
    void collabSubscribe_participant_passes() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Message<byte[]> message = subscribeMessage("/topic/collab/" + sessionId, principalOf(userId));

        assertThatCode(() -> interceptor.preSend(message, null)).doesNotThrowAnyException();
        verify(collaborationApplicationService).verifyParticipant(sessionId, userId);
    }

    @Test
    @DisplayName("협업세션 구독 — 참가자가 아니면 거부")
    void collabSubscribe_notParticipant_rejected() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        doThrow(new IllegalStateException("협업 세션 참가자가 아닙니다."))
                .when(collaborationApplicationService).verifyParticipant(sessionId, userId);
        Message<byte[]> message = subscribeMessage("/topic/collab/" + sessionId, principalOf(userId));

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("관련 없는 목적지 구독은 인가 검사 없이 통과")
    void unrelatedDestination_passesThrough() {
        Message<byte[]> message = subscribeMessage("/topic/analysis/" + UUID.randomUUID(), null);

        assertThatCode(() -> interceptor.preSend(message, null)).doesNotThrowAnyException();
        verifyNoInteractions(graphFacade, collaborationApplicationService);
    }

    @Test
    @DisplayName("SUBSCRIBE가 아닌 명령(SEND)은 검사 없이 통과")
    void nonSubscribeCommand_passesThrough() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination("/app/team/" + UUID.randomUUID() + "/chat");
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatCode(() -> interceptor.preSend(message, null)).doesNotThrowAnyException();
        verifyNoInteractions(graphFacade, collaborationApplicationService);
    }
}
