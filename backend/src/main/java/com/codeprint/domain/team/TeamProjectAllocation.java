// 팀의 프로젝트별 석수 배분 엔티티
package com.codeprint.domain.team;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "team_project_allocations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TeamProjectAllocation {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "team_id", nullable = false, columnDefinition = "uuid")
    private UUID teamId;

    @Column(name = "project_id", nullable = false, columnDefinition = "uuid")
    private UUID projectId;

    @Column(name = "allocated_seats", nullable = false)
    private int allocatedSeats;

    // 신규 배분
    public static TeamProjectAllocation allocate(UUID teamId, UUID projectId, int seats) {
        TeamProjectAllocation a = new TeamProjectAllocation();
        a.id = UUID.randomUUID();
        a.teamId = teamId;
        a.projectId = projectId;
        a.allocatedSeats = seats;
        return a;
    }

    // 배분 석수 변경
    public void updateSeats(int seats) {
        this.allocatedSeats = seats;
    }
}
