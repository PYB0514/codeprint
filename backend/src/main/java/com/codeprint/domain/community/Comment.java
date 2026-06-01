package com.codeprint.domain.community;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "comments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Comment {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "post_id", nullable = false, columnDefinition = "uuid")
    private UUID postId;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static Comment create(UUID postId, UUID userId, String content) {
        Comment comment = new Comment();
        comment.id = UUID.randomUUID();
        comment.postId = postId;
        comment.userId = userId;
        comment.content = content;
        comment.createdAt = Instant.now();
        return comment;
    }

    public CommentId getCommentId() {
        return CommentId.of(id);
    }
}
