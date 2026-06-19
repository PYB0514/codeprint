// architecture_intents 테이블 JPA 엔티티 — 프로젝트당 1행(project_id PK)
package com.codeprint.infrastructure.persistence.graph;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "architecture_intents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ArchitectureIntentEntity {

    @Id
    @Column(name = "project_id", columnDefinition = "uuid")
    private UUID projectId;

    @Column(name = "intent_json", nullable = false, columnDefinition = "TEXT")
    private String intentJson;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // 새 의도 선언 엔티티 생성
    public static ArchitectureIntentEntity of(UUID projectId, String intentJson) {
        ArchitectureIntentEntity e = new ArchitectureIntentEntity();
        e.projectId = projectId;
        e.intentJson = intentJson;
        e.updatedAt = Instant.now();
        return e;
    }

    // 기존 엔티티의 내용을 갱신
    public void update(String intentJson) {
        this.intentJson = intentJson;
        this.updatedAt = Instant.now();
    }
}
