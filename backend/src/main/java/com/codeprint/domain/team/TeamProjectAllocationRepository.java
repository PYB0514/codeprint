// 팀 프로젝트 석수 배분 리포지토리 인터페이스
package com.codeprint.domain.team;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TeamProjectAllocationRepository {

    TeamProjectAllocation save(TeamProjectAllocation allocation);

    void deleteById(UUID id);

    List<TeamProjectAllocation> findByTeamId(UUID teamId);

    // 프로젝트가 배분된 모든 팀 조회 (project → team 역방향, RBAC 접근 확인용)
    List<TeamProjectAllocation> findByProjectId(UUID projectId);

    Optional<TeamProjectAllocation> findByTeamIdAndProjectId(UUID teamId, UUID projectId);

    // 팀 전체 배분 합산 (초과 검증용)
    int sumAllocatedSeatsByTeamId(UUID teamId);
}
