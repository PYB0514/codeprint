// 유저 알림 설정 영속성 포트 — infrastructure 직접 의존 차단
package com.codeprint.domain.notification;

import java.util.Optional;
import java.util.UUID;

public interface NotificationSettingsRepository {
    Optional<UserNotificationSettings> findById(UUID userId);
    UserNotificationSettings save(UserNotificationSettings settings);
}
