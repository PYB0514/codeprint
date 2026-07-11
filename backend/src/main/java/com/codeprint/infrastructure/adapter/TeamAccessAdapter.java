// project 컨텍스트 TeamAccessPort의 team 컨텍스트 어댑터 — 프로젝트가 배분된 팀의 멤버인지 확인
package com.codeprint.infrastructure.adapter;

import com.codeprint.domain.project.port.TeamAccessPort;
import com.codeprint.domain.team.TeamMemberRepository;
import com.codeprint.domain.team.TeamProjectAllocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TeamAccessAdapter implements TeamAccessPort {

    private final TeamProjectAllocationRepository allocationRepository;
    private final TeamMemberRepository memberRepository;

    @Override
    public boolean hasAccessViaTeam(UUID projectId, UUID userId) {
        return allocationRepository.findByProjectId(projectId).stream()
                .anyMatch(alloc -> memberRepository.findByTeamIdAndUserId(alloc.getTeamId(), userId).isPresent());
    }
}
