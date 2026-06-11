// 유저 알림 설정 조회·수정 애플리케이션 서비스
package com.codeprint.application.notification;

import com.codeprint.domain.notification.UserNotificationSettings;
import com.codeprint.infrastructure.persistence.notification.NotificationSettingsJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationSettingsApplicationService {

    private final NotificationSettingsJpaRepository settingsRepository;

    // 알림 설정 조회 (없으면 기본값 반환)
    @Transactional(readOnly = true)
    public UserNotificationSettings get(UUID userId) {
        return settingsRepository.findById(userId)
                .orElseGet(() -> UserNotificationSettings.defaultFor(userId));
    }

    // DM 푸시 허용 여부 조회 — 기본값 true
    @Transactional(readOnly = true)
    public boolean isDmPushEnabled(UUID userId) {
        return settingsRepository.findById(userId)
                .map(UserNotificationSettings::isDm)
                .orElse(true);
    }

    // 알림 설정 저장
    @Transactional
    public UserNotificationSettings update(UUID userId, boolean teamChat, boolean dm) {
        UserNotificationSettings s = settingsRepository.findById(userId)
                .orElseGet(() -> UserNotificationSettings.defaultFor(userId));
        s.update(teamChat, dm);
        return settingsRepository.save(s);
    }
}
