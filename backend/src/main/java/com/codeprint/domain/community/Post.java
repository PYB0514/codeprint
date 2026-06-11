// 커뮤니티 게시글 Aggregate Root 엔티티
package com.codeprint.domain.community;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "posts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Post {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "graph_id", columnDefinition = "uuid")
    private UUID graphId;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(columnDefinition = "text")
    private String content;

    @Column(name = "feedback_type", length = 50)
    private String feedbackType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "hidden_layers", columnDefinition = "jsonb")
    private List<String> hiddenLayers;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "hidden_groups", columnDefinition = "jsonb")
    private List<String> hiddenGroups;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "hidden_node_names", columnDefinition = "jsonb")
    private List<String> hiddenNodeNames;

    @Column(name = "view_count", nullable = false)
    private long viewCount = 0;

    @Column(name = "repo_url", length = 500)
    private String repoUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // 사용자 입력으로 새 게시글 인스턴스 생성
    public static Post create(UUID userId, UUID graphId, String title, String content, String feedbackType,
                              List<String> hiddenLayers, List<String> hiddenGroups, List<String> hiddenNodeNames,
                              String repoUrl) {
        Post post = new Post();
        post.id = UUID.randomUUID();
        post.userId = userId;
        post.graphId = graphId;
        post.title = title;
        post.content = content;
        post.feedbackType = feedbackType;
        post.hiddenLayers = hiddenLayers != null ? hiddenLayers : List.of();
        post.hiddenGroups = hiddenGroups != null ? hiddenGroups : List.of();
        post.hiddenNodeNames = hiddenNodeNames != null ? hiddenNodeNames : List.of();
        post.repoUrl = repoUrl;
        post.createdAt = Instant.now();
        post.updatedAt = Instant.now();
        return post;
    }

    // 조회수 1 증가
    public void incrementViewCount() {
        this.viewCount++;
    }

    // 게시글 제목과 내용을 수정
    public void update(String title, String content) {
        this.title = title;
        this.content = content;
        this.updatedAt = Instant.now();
    }

    // UUID를 PostId Value Object로 변환하여 반환
    public PostId getPostId() {
        return PostId.of(id);
    }
}
