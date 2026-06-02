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

    // PENDING 상태로 새 분석 결과 인스턴스 생성
    public static AnalysisResult create(UUID projectId) {
        AnalysisResult result = new AnalysisResult();
        result.id = UUID.randomUUID();
        result.projectId = projectId;
        result.status = AnalysisStatus.PENDING;
        result.progress = 0;
        result.createdAt = Instant.now();
        return result;
    }

    // 분석을 RUNNING 상태로 전환하고 시작 시각을 기록
    public void start() {
        this.status = AnalysisStatus.RUNNING;
        this.startedAt = Instant.now();
    }

    // 분석 진행률을 0~100 범위로 갱신
    public void updateProgress(int progress) {
        this.progress = Math.min(100, Math.max(0, progress));
    }

    // 분석을 DONE 상태로 완료 처리하고 종료 시각을 기록
    public void complete() {
        this.status = AnalysisStatus.DONE;
        this.progress = 100;
        this.finishedAt = Instant.now();
    }

    // 분석을 FAILED 상태로 전환하고 오류 메시지를 저장
    public void fail(String errorMsg) {
        this.status = AnalysisStatus.FAILED;
        this.errorMsg = errorMsg;
        this.finishedAt = Instant.now();
    }

    // UUID를 AnalysisId Value Object로 변환하여 반환
    public AnalysisId getAnalysisId() {
        return AnalysisId.of(id);
    }
}
