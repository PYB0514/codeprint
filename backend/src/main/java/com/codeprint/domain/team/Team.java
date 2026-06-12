// 팀 플랜을 보유하는 팀 엔티티 (Seat Pool 단위)
package com.codeprint.domain.team;

import com.codeprint.domain.user.UserPlan;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "teams")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Team {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "owner_user_id", nullable = false, columnDefinition = "uuid")
    private UUID ownerUserId;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserPlan plan;

    @Column(name = "total_seats", nullable = false)
    private int totalSeats;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // 팀 생성
    public static Team create(UUID ownerUserId, String name, UserPlan plan) {
        Team t = new Team();
        t.id = UUID.randomUUID();
        t.ownerUserId = ownerUserId;
        t.name = name;
        t.plan = plan;
        t.totalSeats = plan.defaultTotalSeats();
        t.createdAt = Instant.now();
        return t;
    }

    // 팀 플랜 업그레이드
    public void upgradePlan(UserPlan newPlan) {
        this.plan = newPlan;
        this.totalSeats = newPlan.defaultTotalSeats();
    }
}
