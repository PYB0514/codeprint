// 인앱 알림 생성 및 조회 애플리케이션 서비스
package com.codeprint.application.notification;

import com.codeprint.domain.notification.Notification;
import com.codeprint.domain.notification.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    // 알림 비동기 생성 — 주요 이벤트 발생 시 호출
    @Async
    @Transactional
    public void create(UUID userId, String type, String message, String link) {
        notificationRepository.save(Notification.create(userId, type, message, link));
    }

    // 최근 알림 목록 조회
    @Transactional(readOnly = true)
    public List<Notification> getRecent(UUID userId) {
        return notificationRepository.findTop30ByUserIdOrderByCreatedAtDesc(userId);
    }

    // 읽지 않은 알림 수
    @Transactional(readOnly = true)
    public long getUnreadCount(UUID userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    // 단일 알림 읽음 처리
    @Transactional
    public void markRead(UUID notificationId, UUID userId) {
        notificationRepository.findById(notificationId).ifPresent(n -> {
            if (n.getUserId().equals(userId)) n.markRead();
        });
    }

    // 전체 알림 읽음 처리
    @Transactional
    public void markAllRead(UUID userId) {
        notificationRepository.markAllReadByUserId(userId);
    }
}
