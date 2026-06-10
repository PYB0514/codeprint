// 유저 팔로우 관계 엔티티
package com.codeprint.domain.user;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_follows")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserFollow {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "follower_id", nullable = false, columnDefinition = "uuid")
    private UUID followerId;

    @Column(name = "following_id", nullable = false, columnDefinition = "uuid")
    private UUID followingId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // 팔로우 관계 생성
    public static UserFollow create(UUID followerId, UUID followingId) {
        UserFollow f = new UserFollow();
        f.id = UUID.randomUUID();
        f.followerId = followerId;
        f.followingId = followingId;
        f.createdAt = Instant.now();
        return f;
    }
}
