// 협업 세션 커서·선택 이벤트를 STOMP로 브로드캐스트하는 컨트롤러
package com.codeprint.interfaces.websocket;

import com.codeprint.domain.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class CollaborationWebSocketController {

    private final SimpMessagingTemplate messagingTemplate;

    // Principal에서 User 엔티티 추출 (JWT 필터가 User 객체를 principal로 저장)
    private User extractUser(Principal principal) {
        if (principal instanceof UsernamePasswordAuthenticationToken auth
                && auth.getPrincipal() instanceof User u) {
            return u;
        }
        return null;
    }

    // 커서 위치 이벤트를 세션 참가자 전체에게 브로드캐스트
    @MessageMapping("/collab/{sessionId}/cursor")
    public void handleCursor(
            @DestinationVariable String sessionId,
            @Payload Map<String, Object> payload,
            Principal principal) {
        if (principal == null) return;
        User user = extractUser(principal);
        if (user == null) return;
        UUID userId = user.getId();
        String username = user.getUsername();

        Map<String, Object> event = new HashMap<>(payload);
        event.put("userId", userId.toString());
        event.put("username", username);
        event.put("type", "cursor");

        messagingTemplate.convertAndSend("/topic/collab/" + sessionId, event);
    }

    // 노드 선택 이벤트를 세션 참가자 전체에게 브로드캐스트
    @MessageMapping("/collab/{sessionId}/select")
    public void handleSelect(
            @DestinationVariable String sessionId,
            @Payload Map<String, Object> payload,
            Principal principal) {
        if (principal == null) return;
        User user = extractUser(principal);
        if (user == null) return;
        UUID userId = user.getId();
        String username = user.getUsername();

        Map<String, Object> event = new HashMap<>(payload);
        event.put("userId", userId.toString());
        event.put("username", username);
        event.put("type", "select");

        messagingTemplate.convertAndSend("/topic/collab/" + sessionId, event);
    }

    // 팀채팅 메시지를 해당 룸 구독자 전체에게 브로드캐스트 (인증 필수)
    @MessageMapping("/team/{roomId}/chat")
    public void handleTeamChat(
            @DestinationVariable String roomId,
            @Payload Map<String, Object> payload,
            Principal principal) {
        if (principal == null) return;
        User user = extractUser(principal);
        if (user == null) return;

        Map<String, Object> event = Map.of(
                "type", "team_chat",
                "userId", user.getId().toString(),
                "username", user.getUsername(),
                "avatarUrl", user.getAvatarUrl() != null ? user.getAvatarUrl() : "",
                "message", payload.getOrDefault("message", ""),
                "timestamp", java.time.Instant.now().toEpochMilli()
        );

        messagingTemplate.convertAndSend("/topic/team/" + roomId + "/chat", event);
    }

    // 세션 참가/퇴장 이벤트를 참가자 전체에게 브로드캐스트
    @MessageMapping("/collab/{sessionId}/presence")
    public void handlePresence(
            @DestinationVariable String sessionId,
            @Payload Map<String, Object> payload,
            Principal principal) {
        if (principal == null) return;
        User user = extractUser(principal);
        if (user == null) return;
        UUID userId = user.getId();
        String username = user.getUsername();

        Map<String, Object> event = Map.of(
                "userId", userId.toString(),
                "username", username,
                "type", "presence",
                "action", payload.getOrDefault("action", "join")
        );

        messagingTemplate.convertAndSend("/topic/collab/" + sessionId, event);
    }
}
