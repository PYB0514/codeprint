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

    // UUID를 GraphId Value Object로 변환하여 반환
    public GraphId getGraphId() {
        return GraphId.of(id);
    }
}
