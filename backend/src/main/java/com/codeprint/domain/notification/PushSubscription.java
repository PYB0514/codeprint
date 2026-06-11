// Web Push 구독 정보 엔티티 (브라우저별 1개)
package com.codeprint.domain.notification;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "push_subscriptions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String endpoint;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String p256dh;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String auth;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // 구독 등록
    public static PushSubscription of(UUID userId, String endpoint, String p256dh, String auth) {
        PushSubscription s = new PushSubscription();
        s.userId = userId;
        s.endpoint = endpoint;
        s.p256dh = p256dh;
        s.auth = auth;
        s.createdAt = Instant.now();
        return s;
    }
}
