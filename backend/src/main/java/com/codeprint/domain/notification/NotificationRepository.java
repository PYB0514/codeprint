// 인앱 알림 레포지토리 인터페이스
package com.codeprint.domain.notification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository {

    // 알림 저장
    Notification save(Notification notification);

    // ID로 조회
    Optional<Notification> findById(UUID id);

    // 유저의 최근 알림 목록 조회 (최신순 30개)
    List<Notification> findTop30ByUserIdOrderByCreatedAtDesc(UUID userId);

    // 유저의 읽지 않은 알림 수
    long countByUserIdAndReadFalse(UUID userId);

    // 유저의 모든 미읽 알림 읽음 처리
    void markAllReadByUserId(UUID userId);
}
