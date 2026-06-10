// 노드별 사용자 커스텀 스타일 엔티티 — node_styles 테이블
package com.codeprint.domain.graph;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "node_styles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NodeStyle {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "graph_id", nullable = false, columnDefinition = "uuid")
    private UUID graphId;

    @Column(name = "node_id", nullable = false, columnDefinition = "uuid")
    private UUID nodeId;

    @Column(name = "bg_color", length = 20)
    private String bgColor;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // 노드 스타일 생성 또는 업데이트
    public static NodeStyle of(UUID graphId, UUID nodeId, String bgColor) {
        NodeStyle s = new NodeStyle();
        s.id = UUID.randomUUID();
        s.graphId = graphId;
        s.nodeId = nodeId;
        s.bgColor = bgColor;
        s.createdAt = LocalDateTime.now();
        s.updatedAt = LocalDateTime.now();
        return s;
    }

    // 배경색 변경
    public void updateBgColor(String bgColor) {
        this.bgColor = bgColor;
        this.updatedAt = LocalDateTime.now();
    }
}
