// 사용자가 오탐(false positive)이라고 신고한 경고 — suppress와 달리 학습 신호로 쓰임
package com.codeprint.domain.graph;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fp_reports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FpReport {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "project_id", nullable = false, columnDefinition = "uuid")
    private UUID projectId;

    @Column(nullable = false, length = 64)
    private String fingerprint;

    @Column(name = "warning_type", length = 50)
    private String warningType;

    @Column(name = "reporter_id", nullable = false, columnDefinition = "uuid")
    private UUID reporterId;

    @Column(columnDefinition = "text")
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // 오탐 신고 인스턴스 생성
    public static FpReport create(UUID projectId, String fingerprint, String warningType, UUID reporterId, String reason) {
        FpReport r = new FpReport();
        r.id = UUID.randomUUID();
        r.projectId = projectId;
        r.fingerprint = fingerprint;
        r.warningType = warningType;
        r.reporterId = reporterId;
        r.reason = reason;
        r.createdAt = Instant.now();
        return r;
    }
}
