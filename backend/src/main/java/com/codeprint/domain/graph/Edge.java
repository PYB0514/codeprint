// 그래프 엣지 엔티티 (노드 간 관계)
package com.codeprint.domain.graph;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "edges")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Edge {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "graph_id", nullable = false, columnDefinition = "uuid")
    private UUID graphId;

    @Column(name = "edge_identifier", nullable = false, length = 500)
    private String edgeIdentifier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EdgeType type;

    @Column(name = "source_node_id", nullable = false, columnDefinition = "uuid")
    private UUID sourceNodeId;

    @Column(name = "target_node_id", nullable = false, columnDefinition = "uuid")
    private UUID targetNodeId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "is_hidden", nullable = false)
    private boolean isHidden;

    // 두 노드를 잇는 새 엣지 인스턴스 생성
    public static Edge create(UUID graphId, String edgeIdentifier, EdgeType type,
                              UUID sourceNodeId, UUID targetNodeId) {
        Edge edge = new Edge();
        edge.id = UUID.randomUUID();
        edge.graphId = graphId;
        edge.edgeIdentifier = edgeIdentifier;
        edge.type = type;
        edge.sourceNodeId = sourceNodeId;
        edge.targetNodeId = targetNodeId;
        edge.isHidden = false;
        return edge;
    }

    // 엣지 메타데이터 갱신
    public void updateMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    // 엣지의 공개/숨김 상태를 토글
    public void toggleHidden() {
        this.isHidden = !this.isHidden;
    }

    // UUID를 EdgeId Value Object로 변환하여 반환
    public EdgeId getEdgeId() {
        return EdgeId.of(id);
    }
}
