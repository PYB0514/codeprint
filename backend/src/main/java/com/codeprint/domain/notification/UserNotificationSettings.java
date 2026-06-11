// 유저별 알림 수신 설정 엔티티
package com.codeprint.domain.notification;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_notification_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserNotificationSettings {

    @Id
    @Column(name = "user_id", columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "team_chat", nullable = false)
    private boolean teamChat = true;

    @Column(name = "dm", nullable = false)
    private boolean dm = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // 기본 설정 생성
    public static UserNotificationSettings defaultFor(UUID userId) {
        UserNotificationSettings s = new UserNotificationSettings();
        s.userId = userId;
        s.teamChat = true;
        s.dm = true;
        s.createdAt = Instant.now();
        s.updatedAt = Instant.now();
        return s;
    }

    // 설정 업데이트
    public void update(boolean teamChat, boolean dm) {
        this.teamChat = teamChat;
        this.dm = dm;
        this.updatedAt = Instant.now();
    }
}
