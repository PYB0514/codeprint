// 예외(IGNORE) 규칙 추가/제거 이력 기록 Aggregate Root 엔티티 — 팀 거버넌스 감사 로그
package com.codeprint.domain.graph;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "architecture_intent_audit_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ArchitectureIntentAuditLog {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "project_id", nullable = false, columnDefinition = "uuid")
    private UUID projectId;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Column(nullable = false, length = 100)
    private String username;

    @Column(nullable = false, length = 10)
    private String action;

    @Column(name = "rule_type", length = 50)
    private String ruleType;

    @Column(name = "rule_from", length = 200)
    private String ruleFrom;

    @Column(name = "rule_to", length = 200)
    private String ruleTo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // 예외 규칙 추가/제거 이력 기록 생성
    public static ArchitectureIntentAuditLog create(
            UUID projectId, UUID userId, String username, String action,
            String ruleType, String ruleFrom, String ruleTo) {
        ArchitectureIntentAuditLog log = new ArchitectureIntentAuditLog();
        log.id = UUID.randomUUID();
        log.projectId = projectId;
        log.userId = userId;
        log.username = username;
        log.action = action;
        log.ruleType = ruleType;
        log.ruleFrom = ruleFrom;
        log.ruleTo = ruleTo;
        log.createdAt = Instant.now();
        return log;
    }
}
