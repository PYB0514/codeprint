// 코드 분석 결과 Aggregate Root 엔티티
package com.codeprint.domain.analysis;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "analyses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnalysisResult {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "project_id", nullable = false, columnDefinition = "uuid")
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AnalysisStatus status;

    @Column(nullable = false)
    private int progress;

    @Column(name = "error_msg", columnDefinition = "text")
    private String errorMsg;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static AnalysisResult create(UUID projectId) {
        AnalysisResult result = new AnalysisResult();
        result.id = UUID.randomUUID();
        result.projectId = projectId;
        result.status = AnalysisStatus.PENDING;
        result.progress = 0;
        result.createdAt = Instant.now();
        return result;
    }

    public void start() {
        this.status = AnalysisStatus.RUNNING;
        this.startedAt = Instant.now();
    }

    public void updateProgress(int progress) {
        this.progress = Math.min(100, Math.max(0, progress));
    }

    public void complete() {
        this.status = AnalysisStatus.DONE;
        this.progress = 100;
        this.finishedAt = Instant.now();
    }

    public void fail(String errorMsg) {
        this.status = AnalysisStatus.FAILED;
        this.errorMsg = errorMsg;
        this.finishedAt = Instant.now();
    }

    public AnalysisId getAnalysisId() {
        return AnalysisId.of(id);
    }
}
