// 후원 단건 결제 Aggregate Root 엔티티
package com.codeprint.domain.donation;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "donations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Donation {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "user_id", columnDefinition = "uuid")
    private UUID userId;

    @Column(nullable = false, length = 100)
    private String username;

    @Column(nullable = false)
    private long amount;

    @Column(name = "payment_key", nullable = false, length = 300)
    private String paymentKey;

    @Column(name = "order_id", nullable = false, unique = true, length = 200)
    private String orderId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // 토스 결제 승인 완료 후 후원 내역 생성
    public static Donation create(UUID userId, String username, long amount, String paymentKey, String orderId) {
        Donation d = new Donation();
        d.id = UUID.randomUUID();
        d.userId = userId;
        d.username = username;
        d.amount = amount;
        d.paymentKey = paymentKey;
        d.orderId = orderId;
        d.createdAt = Instant.now();
        return d;
    }
}
