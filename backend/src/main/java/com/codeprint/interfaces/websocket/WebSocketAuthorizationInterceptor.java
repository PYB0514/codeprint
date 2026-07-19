// STOMP SUBSCRIBE 시점에 팀채팅·협업세션 토픽 접근 권한을 검증하는 인터셉터 — 익명·비참가자 도청 차단
package com.codeprint.interfaces.websocket;

import com.codeprint.application.collaboration.CollaborationApplicationService;
import com.codeprint.application.graph.GraphFacade;
import com.codeprint.domain.user.User;
import org.springframework.context.annotation.Lazy;
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
public class WebSocketAuthorizationInterceptor implements ChannelInterceptor {

    private final GraphFacade graphFacade;
    private final CollaborationApplicationService collaborationApplicationService;

    // GraphFacade·CollaborationApplicationService 둘 다 (직접 또는
    // CollaborationGraphAccessAdapter 경유로) AnalysisReadAdapter→AnalysisApplicationService→
    // AnalysisRunner→AnalysisProgressHandler→SimpMessagingTemplate→WebSocketConfig로
    // 되돌아오는 순환을 만들어 둘 다 지연 주입으로 끊는다(실제 호출 시점에만 해소).
    public WebSocketAuthorizationInterceptor(@Lazy GraphFacade graphFacade,
                                              @Lazy CollaborationApplicationService collaborationApplicationService) {
        this.graphFacade = graphFacade;
        this.collaborationApplicationService = collaborationApplicationService;
    }

    private static final Pattern TEAM_CHAT_TOPIC =
            Pattern.compile("^/topic/team/([0-9a-fA-F-]{36})/chat$");
    private static final Pattern COLLAB_TOPIC =
            Pattern.compile("^/topic/collab/([0-9a-fA-F-]{36})(?:/.*)?$");
    // 기본 SimpleBroker는 Ant 패턴 구독(*, **, {var})도 매칭시켜주므로, 아래 정규식이
    // 리터럴 UUID만 인식하는 것과 무관하게 와일드카드 구독 자체를 원천 차단해야 우회가 없다.
    private static final Pattern WILDCARD_CHARS = Pattern.compile("[*?{}]");

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

        if (WILDCARD_CHARS.matcher(destination).find()) {
            throw new IllegalArgumentException("와일드카드 구독은 허용되지 않습니다: " + destination);
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
