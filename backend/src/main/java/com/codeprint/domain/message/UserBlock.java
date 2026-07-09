// 쪽지 발신 차단 관계 엔티티
package com.codeprint.domain.message;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_blocks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserBlock {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "blocker_id", nullable = false, columnDefinition = "uuid")
    private UUID blockerId;

    @Column(name = "blocked_id", nullable = false, columnDefinition = "uuid")
    private UUID blockedId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // 차단 관계 생성
    public static UserBlock of(UUID blockerId, UUID blockedId) {
        UserBlock b = new UserBlock();
        b.id = UUID.randomUUID();
        b.blockerId = blockerId;
        b.blockedId = blockedId;
        b.createdAt = Instant.now();
        return b;
    }
}
