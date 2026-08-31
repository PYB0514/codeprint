// 그래프 Aggregate Root 엔티티
package com.codeprint.domain.graph;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
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

    // SMALLINT(int2) 컬럼과 매핑 일치를 위해 Short — 슬롯 값은 1~5
    @Column(name = "pinned_slot")
    private Short pinnedSlot;

    // 사전계산된 구조 경고 — null이면 미계산(레거시 또는 무효화됨), 조회 시 지연 계산. Node.metadata와 동일 매핑
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "warnings", columnDefinition = "jsonb")
    private List<Map<String, Object>> warnings;

    // warnings를 계산한 시점의 감지 규칙 버전 — 현재 버전과 다르면 stale로 보고 재계산
    @Column(name = "warnings_ruleset_version")
    private Short warningsRulesetVersion;

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
        this.pinnedSlot = (short) slot;
        this.updatedAt = Instant.now();
    }

    // 사전계산된 구조 경고를 규칙 버전과 함께 적재 — 파생 캐시라 updatedAt은 건드리지 않는다. null 전달 시 무효화
    public void cacheWarnings(List<Map<String, Object>> warnings, short rulesetVersion) {
        this.warnings = warnings;
        this.warningsRulesetVersion = warnings == null ? null : rulesetVersion;
    }

    // 사전계산 경고가 주어진 규칙 버전 기준으로 최신인지 — 버전 불일치 또는 미계산이면 false
    public boolean hasFreshWarnings(short currentRulesetVersion) {
        return warnings != null && warningsRulesetVersion != null && warningsRulesetVersion == currentRulesetVersion;
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
