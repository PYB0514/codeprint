// STOMP SUBSCRIBE 시점에 팀채팅·협업세션 토픽 접근 권한을 검증하는 인터셉터 — 익명·비참가자 도청 차단
package com.codeprint.interfaces.websocket;

import com.codeprint.application.collaboration.CollaborationApplicationService;
import com.codeprint.application.graph.GraphFacade;
import com.codeprint.domain.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class WebSocketAuthorizationInterceptor implements ChannelInterceptor {

    private final GraphFacade graphFacade;
    private final CollaborationApplicationService collaborationApplicationService;

    private static final Pattern TEAM_CHAT_TOPIC =
            Pattern.compile("^/topic/team/([0-9a-fA-F-]{36})/chat$");
    private static final Pattern COLLAB_TOPIC =
            Pattern.compile("^/topic/collab/([0-9a-fA-F-]{36})(?:/.*)?$");

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (accessor.getCommand() != StompCommand.SUBSCRIBE) {
            return message;
        }

        String destination = accessor.getDestination();
        if (destination == null) {
            return message;
        }

        Matcher teamMatcher = TEAM_CHAT_TOPIC.matcher(destination);
        if (teamMatcher.matches()) {
            UUID userId = requireAuthenticatedUserId(accessor.getUser());
            graphFacade.verifyGraphOwnership(UUID.fromString(teamMatcher.group(1)), userId);
            return message;
        }

        Matcher collabMatcher = COLLAB_TOPIC.matcher(destination);
        if (collabMatcher.matches()) {
            UUID userId = requireAuthenticatedUserId(accessor.getUser());
            collaborationApplicationService.verifyParticipant(UUID.fromString(collabMatcher.group(1)), userId);
        }

        return message;
    }

    // Principal에서 인증된 사용자 ID 추출 — 미인증이면 예외로 구독 거부
    private UUID requireAuthenticatedUserId(Principal principal) {
        if (principal instanceof UsernamePasswordAuthenticationToken auth
                && auth.getPrincipal() instanceof User user) {
            return user.getId();
        }
        throw new IllegalStateException("인증되지 않은 구독입니다.");
    }
}
