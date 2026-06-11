// 유저 간 쪽지 전송·조회 REST API
package com.codeprint.interfaces.api;

import com.codeprint.application.message.MessageApplicationService;
import com.codeprint.domain.message.DirectMessage;
import com.codeprint.domain.notification.UserNotificationSettings;
import com.codeprint.domain.user.User;
import com.codeprint.infrastructure.persistence.notification.NotificationSettingsJpaRepository;
import com.codeprint.infrastructure.push.WebPushService;
import com.codeprint.infrastructure.security.JwtTokenProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageApplicationService messageService;
    private final WebPushService webPushService;
    private final NotificationSettingsJpaRepository notificationSettingsRepository;

    // Principal에서 로그인 사용자 ID 추출
    private UUID currentUserId(Principal principal) {
        if (principal instanceof UsernamePasswordAuthenticationToken auth
                && auth.getPrincipal() instanceof User u) {
            return u.getId();
        }
        throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }

    record SendRequest(@NotBlank @Size(max = 1000) String content) {}

    record MessageResponse(
        UUID id, UUID senderId, String senderUsername, String senderAvatarUrl,
        UUID receiverId, String receiverUsername, String receiverAvatarUrl,
        String content, Instant readAt, Instant createdAt
    ) {}

    record UnreadCountResponse(long count) {}

    // 안 읽은 쪽지 수 조회
    @GetMapping("/unread-count")
    public UnreadCountResponse unreadCount(Principal principal) {
        return new UnreadCountResponse(messageService.countUnread(currentUserId(principal)));
    }

    // 받은 쪽지함 — 대화 상대별 최신 메시지 목록
    @GetMapping("/inbox")
    public List<MessageResponse> inbox(@RequestParam(defaultValue = "0") int page, Principal principal) {
        UUID myId = currentUserId(principal);
        return messageService.getInbox(myId, page).stream()
            .map(dm -> toResponse(dm, messageService))
            .toList();
    }

    // 특정 유저와의 대화 스레드
    @GetMapping("/thread/{userId}")
    public List<MessageResponse> thread(@PathVariable UUID userId,
                                        @RequestParam(defaultValue = "0") int page,
                                        Principal principal) {
        UUID myId = currentUserId(principal);
        return messageService.getThread(myId, userId, page).stream()
            .map(dm -> toResponse(dm, messageService))
            .toList();
    }

    // 쪽지 전송
    @PostMapping("/{receiverId}")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse send(@PathVariable UUID receiverId,
                                @Valid @RequestBody SendRequest req,
                                Principal principal) {
        UUID senderId = currentUserId(principal);
        DirectMessage dm = messageService.send(senderId, receiverId, req.content());
        boolean dmPushEnabled = notificationSettingsRepository.findById(receiverId)
                .map(UserNotificationSettings::isDm).orElse(true);
        if (dmPushEnabled) {
            User sender = messageService.getUser(senderId);
            webPushService.sendToUser(receiverId, sender.getUsername() + "님의 쪽지",
                    req.content().length() > 60 ? req.content().substring(0, 60) + "…" : req.content());
        }
        return toResponse(dm, messageService);
    }

    // 쪽지 읽음 처리
    @PutMapping("/{messageId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@PathVariable UUID messageId, Principal principal) {
        messageService.markRead(messageId, currentUserId(principal));
    }

    // DirectMessage -> MessageResponse 변환
    private MessageResponse toResponse(DirectMessage dm, MessageApplicationService svc) {
        User sender = svc.getUser(dm.getSenderId());
        User receiver = svc.getUser(dm.getReceiverId());
        return new MessageResponse(
            dm.getId(), dm.getSenderId(), sender.getUsername(), sender.getAvatarUrl(),
            dm.getReceiverId(), receiver.getUsername(), receiver.getAvatarUrl(),
            dm.getContent(), dm.getReadAt(), dm.getCreatedAt()
        );
    }
}
