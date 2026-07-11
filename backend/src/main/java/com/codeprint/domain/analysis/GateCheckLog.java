// PR 게이트 체크 결과 기록 Aggregate Root 엔티티 — 지표 대시보드 집계용
package com.codeprint.domain.analysis;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "gate_check_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GateCheckLog {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "project_id", nullable = false, columnDefinition = "uuid")
    private UUID projectId;

    @Column(name = "pr_number", nullable = false)
    private int prNumber;

    @Column(nullable = false, length = 20)
    private String state;

    @Column(name = "high_count", nullable = false)
    private int highCount;

    @Column(name = "warning_count", nullable = false)
    private int warningCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // PR 게이트 체크 결과를 새 기록으로 생성
    public static GateCheckLog create(UUID projectId, int prNumber, String state, int highCount, int warningCount) {
        GateCheckLog log = new GateCheckLog();
        log.id = UUID.randomUUID();
        log.projectId = projectId;
        log.prNumber = prNumber;
        log.state = state;
        log.highCount = highCount;
        log.warningCount = warningCount;
        log.createdAt = Instant.now();
        return log;
    }
}
