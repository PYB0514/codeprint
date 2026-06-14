// 관리자의 사용자 플랜 변경 감사 로그 엔티티 (append-only)
package com.codeprint.domain.admin;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "plan_grant_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlanGrantLog {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "actor_admin_id", columnDefinition = "uuid", nullable = false)
    private UUID actorAdminId;

    @Column(name = "target_user_id", columnDefinition = "uuid", nullable = false)
    private UUID targetUserId;

    @Column(name = "old_plan", nullable = false, length = 20)
    private String oldPlan;

    @Column(name = "new_plan", nullable = false, length = 20)
    private String newPlan;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // 플랜 변경 감사 로그 생성
    public static PlanGrantLog create(UUID actorAdminId, UUID targetUserId, String oldPlan, String newPlan, String reason) {
        PlanGrantLog log = new PlanGrantLog();
        log.id = UUID.randomUUID();
        log.actorAdminId = actorAdminId;
        log.targetUserId = targetUserId;
        log.oldPlan = oldPlan;
        log.newPlan = newPlan;
        log.reason = reason;
        log.createdAt = Instant.now();
        return log;
    }
}
