// 팀 소속 멤버 엔티티
package com.codeprint.domain.team;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "team_members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TeamMember {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "team_id", nullable = false, columnDefinition = "uuid")
    private UUID teamId;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TeamRole role;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    // 멤버 추가 (팀장은 create 시 별도 호출)
    public static TeamMember add(UUID teamId, UUID userId, TeamRole role) {
        TeamMember m = new TeamMember();
        m.id = UUID.randomUUID();
        m.teamId = teamId;
        m.userId = userId;
        m.role = role;
        m.joinedAt = Instant.now();
        return m;
    }
}
