// 게시글에 첨부된 그래프 스냅샷 — 등록 시점의 프리셋 설정을 얼려서 저장(이후 원본 프리셋이 바뀌어도 영향 없음)
package com.codeprint.domain.community;

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
@Table(name = "post_graph_snapshots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostGraphSnapshot {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "post_id", nullable = false, columnDefinition = "uuid")
    private UUID postId;

    @Column(name = "project_id", nullable = false, columnDefinition = "uuid")
    private UUID projectId;

    @Column(name = "graph_id", nullable = false, columnDefinition = "uuid")
    private UUID graphId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> config;

    @Column(nullable = false)
    private int position;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // 등록 시점의 프리셋 config를 복사해 스냅샷 생성
    public static PostGraphSnapshot create(UUID postId, UUID projectId, UUID graphId, Map<String, Object> config, int position) {
        PostGraphSnapshot s = new PostGraphSnapshot();
        s.id = UUID.randomUUID();
        s.postId = postId;
        s.projectId = projectId;
        s.graphId = graphId;
        s.config = config;
        s.position = position;
        s.createdAt = Instant.now();
        return s;
    }
}
