// 유저 간 1:1 쪽지 엔티티
package com.codeprint.domain.message;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "direct_messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DirectMessage {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "sender_id", nullable = false, columnDefinition = "uuid")
    private UUID senderId;

    @Column(name = "receiver_id", nullable = false, columnDefinition = "uuid")
    private UUID receiverId;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // 쪽지 생성
    public static DirectMessage of(UUID senderId, UUID receiverId, String content) {
        DirectMessage dm = new DirectMessage();
        dm.id = UUID.randomUUID();
        dm.senderId = senderId;
        dm.receiverId = receiverId;
        dm.content = content;
        dm.createdAt = Instant.now();
        return dm;
    }

    // 쪽지 읽음 처리
    public void markAsRead() {
        if (this.readAt == null) {
            this.readAt = Instant.now();
        }
    }
}
