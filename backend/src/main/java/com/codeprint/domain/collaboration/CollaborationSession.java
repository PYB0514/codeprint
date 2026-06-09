// 실시간 협업 세션 Aggregate Root
package com.codeprint.domain.collaboration;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "collaboration_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CollaborationSession {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "graph_id", nullable = false, columnDefinition = "uuid")
    private UUID graphId;

    @Column(name = "owner_id", nullable = false, columnDefinition = "uuid")
    private UUID ownerId;

    @Column(name = "invite_code", nullable = false, unique = true, length = 8)
    private String inviteCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<SessionParticipant> participants = new ArrayList<>();

    // 그래프 소유자가 협업 세션 생성
    public static CollaborationSession create(UUID graphId, UUID ownerId, String inviteCode) {
        CollaborationSession s = new CollaborationSession();
        s.id = UUID.randomUUID();
        s.graphId = graphId;
        s.ownerId = ownerId;
        s.inviteCode = inviteCode;
        s.createdAt = Instant.now();
        return s;
    }

    // 참가자가 이미 세션에 있는지 확인
    public boolean hasParticipant(UUID userId) {
        return participants.stream().anyMatch(p -> p.getUserId().equals(userId));
    }

    // 사용자를 세션 참가자로 추가
    public void addParticipant(UUID userId) {
        if (!hasParticipant(userId)) {
            participants.add(SessionParticipant.create(this, userId));
        }
    }
}
