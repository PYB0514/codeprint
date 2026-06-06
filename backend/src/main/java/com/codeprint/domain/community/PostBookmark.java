// 게시글 북마크 엔티티
package com.codeprint.domain.community;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "post_bookmarks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostBookmark {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "post_id", nullable = false, columnDefinition = "uuid")
    private UUID postId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // 북마크 생성
    public static PostBookmark of(UUID userId, UUID postId) {
        PostBookmark b = new PostBookmark();
        b.id = UUID.randomUUID();
        b.userId = userId;
        b.postId = postId;
        b.createdAt = Instant.now();
        return b;
    }
}
