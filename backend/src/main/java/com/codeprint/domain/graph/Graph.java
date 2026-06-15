// 그래프 Aggregate Root 엔티티
package com.codeprint.domain.graph;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "graphs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Graph {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "project_id", nullable = false, columnDefinition = "uuid")
    private UUID projectId;

    @Column(name = "analysis_id", nullable = false, columnDefinition = "uuid")
    private UUID analysisId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "analyzed_file_count")
    private Integer analyzedFileCount;

    @Column(name = "total_file_count")
    private Integer totalFileCount;

    @Column(name = "pinned_slot")
    private Integer pinnedSlot;

    // 프로젝트 ID와 분석 ID로 새 그래프 인스턴스 생성
    public static Graph create(UUID projectId, UUID analysisId) {
        Graph graph = new Graph();
        graph.id = UUID.randomUUID();
        graph.projectId = projectId;
        graph.analysisId = analysisId;
        graph.createdAt = Instant.now();
        graph.updatedAt = Instant.now();
        return graph;
    }

    // updatedAt 타임스탬프를 현재 시각으로 갱신
    public void touch() {
        this.updatedAt = Instant.now();
    }

    // 분석된 파일 수와 전체 대상 파일 수 기록 — 대형 레포 절단 안내용
    public void recordFileCounts(int analyzed, int total) {
        this.analyzedFileCount = analyzed;
        this.totalFileCount = total;
    }

    // 버전을 고정 슬롯에 고정 — 보존 정책 삭제 대상에서 제외 (슬롯 1~5)
    public void pin(int slot) {
        if (slot < 1 || slot > 5) {
            throw new IllegalArgumentException("고정 슬롯은 1~5만 허용됩니다: " + slot);
        }
        this.pinnedSlot = slot;
        this.updatedAt = Instant.now();
    }

    // 고정 해제 — 다시 보존 정책 대상이 됨
    public void unpin() {
        this.pinnedSlot = null;
        this.updatedAt = Instant.now();
    }

    // 고정 여부
    public boolean isPinned() {
        return pinnedSlot != null;
    }

    // UUID를 GraphId Value Object로 변환하여 반환
    public GraphId getGraphId() {
        return GraphId.of(id);
    }
}
