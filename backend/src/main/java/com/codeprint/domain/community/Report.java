// 게시글·댓글 신고 엔티티
package com.codeprint.domain.community;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Report {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "reporter_id", nullable = false, columnDefinition = "uuid")
    private UUID reporterId;

    // 신고 대상 종류 — POST(게시글) / COMMENT(댓글)
    @Column(name = "target_type", nullable = false, length = 20)
    private String targetType;

    @Column(name = "target_id", nullable = false, columnDefinition = "uuid")
    private UUID targetId;

    @Column(nullable = false, columnDefinition = "text")
    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // 처리 상태 — OPEN(미처리) / RESOLVED(처리 완료)
    @Column(nullable = false, length = 20)
    private String status;

    // 신고 생성
    public static Report create(UUID reporterId, String targetType, UUID targetId, String reason) {
        Report r = new Report();
        r.id = UUID.randomUUID();
        r.reporterId = reporterId;
        r.targetType = targetType;
        r.targetId = targetId;
        r.reason = reason;
        r.createdAt = Instant.now();
        r.status = "OPEN";
        return r;
    }

    // 신고를 처리 완료로 표시
    public void resolve() {
        this.status = "RESOLVED";
    }

    // 신고를 미처리로 되돌림
    public void reopen() {
        this.status = "OPEN";
    }
}
