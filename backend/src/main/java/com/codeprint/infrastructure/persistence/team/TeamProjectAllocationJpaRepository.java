// 팀 프로젝트 석수 배분 JPA 리포지토리
package com.codeprint.infrastructure.persistence.team;

import com.codeprint.domain.team.TeamProjectAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TeamProjectAllocationJpaRepository extends JpaRepository<TeamProjectAllocation, UUID> {
    List<TeamProjectAllocation> findByTeamId(UUID teamId);
    Optional<TeamProjectAllocation> findByTeamIdAndProjectId(UUID teamId, UUID projectId);

    // 팀 전체 배분 합산
    @Query("SELECT COALESCE(SUM(a.allocatedSeats), 0) FROM TeamProjectAllocation a WHERE a.teamId = :teamId")
    int sumAllocatedSeatsByTeamId(UUID teamId);
}
