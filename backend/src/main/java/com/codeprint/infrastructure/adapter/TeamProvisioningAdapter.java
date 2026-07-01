// Payment TeamProvisioningPort의 team 컨텍스트 어댑터 — TeamRepository/TeamMemberRepository로 생성·석수 변경 수행
package com.codeprint.infrastructure.adapter;

import com.codeprint.domain.payment.port.TeamProvisioningPort;
import com.codeprint.domain.team.Team;
import com.codeprint.domain.team.TeamMember;
import com.codeprint.domain.team.TeamMemberRepository;
import com.codeprint.domain.team.TeamRepository;
import com.codeprint.domain.team.TeamRole;
import com.codeprint.shared.plan.UserPlan;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TeamProvisioningAdapter implements TeamProvisioningPort {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository memberRepository;

    // 결제 완료 후 팀 생성 — 팀장을 OWNER로 등록
    @Override
    @Transactional
    public UUID createTeam(UUID ownerUserId, String teamName, int seats) {
        Team team = Team.create(ownerUserId, teamName, UserPlan.DESKTOP, seats);
        teamRepository.save(team);
        memberRepository.save(TeamMember.add(team.getId(), ownerUserId, TeamRole.OWNER));
        return team.getId();
    }

    // 결제 완료 후 기존 팀의 좌석 수 변경
    @Override
    @Transactional
    public void changeSeats(UUID teamId, int newSeats) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new IllegalArgumentException("팀을 찾을 수 없습니다: " + teamId));
        team.upgradePlan(UserPlan.DESKTOP, newSeats);
        teamRepository.save(team);
    }

    // 좌석 증가 결제 준비 시 필요한 팀 소유자·현재 좌석 수 조회
    @Override
    public TeamSummary getTeamSummary(UUID teamId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new IllegalArgumentException("팀을 찾을 수 없습니다: " + teamId));
        return new TeamSummary(team.getOwnerUserId(), team.getTotalSeats());
    }
}
