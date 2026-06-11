// 알림 설정 JPA 레포지토리
package com.codeprint.infrastructure.persistence.notification;

import com.codeprint.domain.notification.UserNotificationSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NotificationSettingsJpaRepository extends JpaRepository<UserNotificationSettings, UUID> {
}
