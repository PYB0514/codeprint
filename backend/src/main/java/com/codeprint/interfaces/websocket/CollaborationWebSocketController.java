// 협업 세션 커서·선택 이벤트를 STOMP로 브로드캐스트하는 컨트롤러
package com.codeprint.interfaces.websocket;

import com.codeprint.domain.user.User;
import com.codeprint.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class CollaborationWebSocketController {

    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userJpaRepository;

    // 커서 위치 이벤트를 세션 참가자 전체에게 브로드캐스트
    @MessageMapping("/collab/{sessionId}/cursor")
    public void handleCursor(
            @DestinationVariable String sessionId,
            @Payload Map<String, Object> payload,
            Principal principal) {
        if (principal == null) return;
        UUID userId = UUID.fromString(principal.getName());
        String username = userJpaRepository.findById(userId)
                .map(User::getUsername).orElse("알 수 없음");

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
        UUID userId = UUID.fromString(principal.getName());
        String username = userJpaRepository.findById(userId)
                .map(User::getUsername).orElse("알 수 없음");

        Map<String, Object> event = new HashMap<>(payload);
        event.put("userId", userId.toString());
        event.put("username", username);
        event.put("type", "select");

        messagingTemplate.convertAndSend("/topic/collab/" + sessionId, event);
    }

    // 세션 참가/퇴장 이벤트를 참가자 전체에게 브로드캐스트
    @MessageMapping("/collab/{sessionId}/presence")
    public void handlePresence(
            @DestinationVariable String sessionId,
            @Payload Map<String, Object> payload,
            Principal principal) {
        if (principal == null) return;
        UUID userId = UUID.fromString(principal.getName());
        String username = userJpaRepository.findById(userId)
                .map(User::getUsername).orElse("알 수 없음");

        Map<String, Object> event = Map.of(
                "userId", userId.toString(),
                "username", username,
                "type", "presence",
                "action", payload.getOrDefault("action", "join")
        );

        messagingTemplate.convertAndSend("/topic/collab/" + sessionId, event);
    }
}
