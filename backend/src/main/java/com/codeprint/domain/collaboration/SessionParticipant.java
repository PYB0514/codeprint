// 협업 세션 참가자 엔티티
package com.codeprint.domain.collaboration;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "session_participants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SessionParticipant {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private CollaborationSession session;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    // 세션과 사용자 ID로 참가자 인스턴스 생성
    static SessionParticipant create(CollaborationSession session, UUID userId) {
        SessionParticipant p = new SessionParticipant();
        p.id = UUID.randomUUID();
        p.session = session;
        p.userId = userId;
        p.joinedAt = Instant.now();
        return p;
    }
}
