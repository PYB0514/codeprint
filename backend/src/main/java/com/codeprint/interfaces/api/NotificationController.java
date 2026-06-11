// 인앱 알림 REST API
package com.codeprint.interfaces.api;

import com.codeprint.application.notification.NotificationService;
import com.codeprint.domain.notification.Notification;
import com.codeprint.domain.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    // 최근 알림 목록 + 미읽 수 조회
    @GetMapping
    public ResponseEntity<Map<String, Object>> getNotifications(@AuthenticationPrincipal User user) {
        List<Map<String, Object>> items = notificationService.getRecent(user.getId()).stream()
                .map(this::toResponse)
                .toList();
        long unread = notificationService.getUnreadCount(user.getId());
        return ResponseEntity.ok(Map.of("items", items, "unreadCount", unread));
    }

    // 단일 알림 읽음 처리
    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        notificationService.markRead(id, user.getId());
        return ResponseEntity.noContent().build();
    }

    // 전체 알림 읽음 처리
    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllRead(@AuthenticationPrincipal User user) {
        notificationService.markAllRead(user.getId());
        return ResponseEntity.noContent().build();
    }

    // Notification → 응답 Map 변환
    private Map<String, Object> toResponse(Notification n) {
        return Map.of(
                "id", n.getId(),
                "type", n.getType(),
                "message", n.getMessage(),
                "link", n.getLink() != null ? n.getLink() : "",
                "isRead", n.isRead(),
                "createdAt", n.getCreatedAt().toEpochMilli()
        );
    }
}
