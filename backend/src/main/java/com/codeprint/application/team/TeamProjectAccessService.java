// 팀에 배분된 프로젝트 조회 — MCP 팀 교차조회(API 키 인가) 전용
package com.codeprint.application.team;

import com.codeprint.domain.team.TeamProjectAllocation;
import com.codeprint.domain.team.TeamProjectAllocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TeamProjectAccessService {

    private final TeamProjectAllocationRepository allocationRepository;

    // 팀에 배분된 모든 프로젝트 ID 조회
    public List<UUID> getAllocatedProjectIds(UUID teamId) {
        return allocationRepository.findByTeamId(teamId).stream()
                .map(TeamProjectAllocation::getProjectId)
                .toList();
    }

    // 프로젝트가 이 팀에 배분돼 있는지 확인
    public boolean isAllocatedToTeam(UUID projectId, UUID teamId) {
        return allocationRepository.findByTeamIdAndProjectId(teamId, projectId).isPresent();
    }
}
