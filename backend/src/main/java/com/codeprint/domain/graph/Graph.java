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

    public static Graph create(UUID projectId, UUID analysisId) {
        Graph graph = new Graph();
        graph.id = UUID.randomUUID();
        graph.projectId = projectId;
        graph.analysisId = analysisId;
        graph.createdAt = Instant.now();
        graph.updatedAt = Instant.now();
        return graph;
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }

    public GraphId getGraphId() {
        return GraphId.of(id);
    }
}
