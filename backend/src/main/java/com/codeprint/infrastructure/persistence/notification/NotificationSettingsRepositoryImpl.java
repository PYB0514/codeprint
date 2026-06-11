// NotificationSettingsRepository 도메인 인터페이스의 JPA 구현체
package com.codeprint.infrastructure.persistence.notification;

import com.codeprint.domain.notification.NotificationSettingsRepository;
import com.codeprint.domain.notification.UserNotificationSettings;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class NotificationSettingsRepositoryImpl implements NotificationSettingsRepository {

    private final NotificationSettingsJpaRepository jpaRepository;

    @Override
    public Optional<UserNotificationSettings> findById(UUID userId) {
        return jpaRepository.findById(userId);
    }

    @Override
    public UserNotificationSettings save(UserNotificationSettings settings) {
        return jpaRepository.save(settings);
    }
}
