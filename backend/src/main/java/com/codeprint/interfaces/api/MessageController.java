// 유저 간 쪽지 전송·조회 REST API
package com.codeprint.interfaces.api;

import com.codeprint.application.message.MessageApplicationService;
import com.codeprint.application.message.UserSummaryDto;
import com.codeprint.domain.message.DirectMessage;
import com.codeprint.shared.event.MessageSentEvent;
import com.codeprint.domain.user.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageApplicationService messageService;
    private final ApplicationEventPublisher eventPublisher;

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
        return toResponses(messageService.getInbox(myId, page));
    }

    // 특정 유저와의 대화 스레드
    @GetMapping("/thread/{userId}")
    public List<MessageResponse> thread(@PathVariable UUID userId,
                                        @RequestParam(defaultValue = "0") int page,
                                        Principal principal) {
        UUID myId = currentUserId(principal);
        return toResponses(messageService.getThread(myId, userId, page));
    }

    // 쪽지 전송
    @PostMapping("/{receiverId}")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse send(@PathVariable UUID receiverId,
                                @Valid @RequestBody SendRequest req,
                                Principal principal) {
        UUID senderId = currentUserId(principal);
        DirectMessage dm = messageService.send(senderId, receiverId, req.content());
        UserSummaryDto sender = messageService.getUser(senderId);
        eventPublisher.publishEvent(new MessageSentEvent(senderId, sender.username(), receiverId, req.content()));
        return toResponse(dm, messageService);
    }

    // 쪽지 읽음 처리
    @PutMapping("/{messageId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@PathVariable UUID messageId, Principal principal) {
        messageService.markRead(messageId, currentUserId(principal));
    }

    // 사용자 차단
    @PostMapping("/block/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void block(@PathVariable UUID userId, Principal principal) {
        messageService.block(currentUserId(principal), userId);
    }

    // 사용자 차단 해제
    @DeleteMapping("/block/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unblock(@PathVariable UUID userId, Principal principal) {
        messageService.unblock(currentUserId(principal), userId);
    }

    // 내가 차단한 사용자 ID 목록
    @GetMapping("/blocks")
    public List<UUID> blockedUserIds(Principal principal) {
        return messageService.getBlockedUserIds(currentUserId(principal));
    }

    // DirectMessage 목록 -> MessageResponse 목록 — 작성자/수신자 유저를 한 번에 배치 조회해 N+1 제거
    private List<MessageResponse> toResponses(List<DirectMessage> messages) {
        if (messages.isEmpty()) return List.of();
        Set<UUID> userIds = new HashSet<>();
        for (DirectMessage dm : messages) {
            userIds.add(dm.getSenderId());
            userIds.add(dm.getReceiverId());
        }
        Map<UUID, UserSummaryDto> users = messageService.getUsers(userIds);
        return messages.stream().map(dm -> {
            UserSummaryDto sender = users.get(dm.getSenderId());
            UserSummaryDto receiver = users.get(dm.getReceiverId());
            return new MessageResponse(
                dm.getId(),
                dm.getSenderId(), sender != null ? sender.username() : "unknown", sender != null ? sender.avatarUrl() : null,
                dm.getReceiverId(), receiver != null ? receiver.username() : "unknown", receiver != null ? receiver.avatarUrl() : null,
                dm.getContent(), dm.getReadAt(), dm.getCreatedAt()
            );
        }).toList();
    }

    // DirectMessage -> MessageResponse 변환 (단건 — 쪽지 전송 응답)
    private MessageResponse toResponse(DirectMessage dm, MessageApplicationService svc) {
        UserSummaryDto sender = svc.getUser(dm.getSenderId());
        UserSummaryDto receiver = svc.getUser(dm.getReceiverId());
        return new MessageResponse(
            dm.getId(), dm.getSenderId(), sender.username(), sender.avatarUrl(),
            dm.getReceiverId(), receiver.username(), receiver.avatarUrl(),
            dm.getContent(), dm.getReadAt(), dm.getCreatedAt()
        );
    }
}
