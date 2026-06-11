// 그래프 노드에 사용자가 남기는 코멘트 엔티티
package com.codeprint.domain.graph;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "node_comments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NodeComment {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "graph_id", nullable = false, columnDefinition = "uuid")
    private UUID graphId;

    @Column(name = "node_id", nullable = false)
    private String nodeId;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // 새 노드 코멘트 생성
    public static NodeComment create(UUID graphId, String nodeId, UUID userId, String content) {
        NodeComment c = new NodeComment();
        c.id = UUID.randomUUID();
        c.graphId = graphId;
        c.nodeId = nodeId;
        c.userId = userId;
        c.content = content;
        c.createdAt = Instant.now();
        return c;
    }

    // 코멘트 작성자 확인
    public boolean isOwner(UUID userId) {
        return this.userId.equals(userId);
    }
}
