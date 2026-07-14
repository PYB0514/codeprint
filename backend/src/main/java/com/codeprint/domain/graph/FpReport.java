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

    // 재현 검증용 구조적 필드 — 신고 시점 경고 원문(자가개선 루프 벤치 오라클과 동일 포맷)
    @Column(columnDefinition = "text")
    private String message;

    @Column(name = "file_path", length = 500)
    private String filePath;

    private Integer line;

    private Integer col;

    @Column(name = "end_col")
    private Integer endCol;

    // 최선노력 코드 스니펫 — GitHub 공개 레포에서만 채워짐(비공개·로컬은 null)
    @Column(name = "code_snippet", columnDefinition = "text")
    private String codeSnippet;

    // 오탐 신고 인스턴스 생성
    public static FpReport create(UUID projectId, String fingerprint, String warningType, UUID reporterId, String reason,
                                   String message, String filePath, Integer line, Integer col, Integer endCol, String codeSnippet) {
        FpReport r = new FpReport();
        r.id = UUID.randomUUID();
        r.projectId = projectId;
        r.fingerprint = fingerprint;
        r.warningType = warningType;
        r.reporterId = reporterId;
        r.reason = reason;
        r.message = message;
        r.filePath = filePath;
        r.line = line;
        r.col = col;
        r.endCol = endCol;
        r.codeSnippet = codeSnippet;
        r.createdAt = Instant.now();
        return r;
    }
}
