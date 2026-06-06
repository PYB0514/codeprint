// 그래프 뷰 프리셋 엔티티 — 사용자별 최대 4개 슬롯
package com.codeprint.domain.graph;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "graph_view_presets",
        uniqueConstraints = @UniqueConstraint(columnNames = {"graph_id", "user_id", "slot"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GraphViewPreset {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "graph_id", nullable = false)
    private UUID graphId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private int slot;

    @Column(nullable = false, length = 30)
    private String name;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> config;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // 슬롯에 프리셋 저장 (신규 또는 덮어쓰기)
    public static GraphViewPreset of(UUID graphId, UUID userId, int slot, String name, Map<String, Object> config) {
        GraphViewPreset p = new GraphViewPreset();
        p.id = UUID.randomUUID();
        p.graphId = graphId;
        p.userId = userId;
        p.slot = slot;
        p.name = name;
        p.config = config;
        p.updatedAt = Instant.now();
        return p;
    }
}
