// 팀 생성·멤버 관리·석수 배분 애플리케이션 서비스
package com.codeprint.application.team;

import com.codeprint.domain.team.*;
import com.codeprint.shared.plan.UserPlan;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TeamApplicationService {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository memberRepository;
    private final TeamProjectAllocationRepository allocationRepository;

    // 팀 삭제 — 팀장만 가능, 멤버·석수배분은 DB CASCADE로 함께 삭제
    @Transactional
    public void deleteTeam(UUID teamId, UUID requesterId) {
        Team team = getTeamOrThrow(teamId);
        verifyOwner(team, requesterId);
        teamRepository.deleteById(teamId);
    }

    // 팀원 추가 (초대 수락 시 호출)
    @Transactional
    public TeamMember addMember(UUID teamId, UUID requesterId, UUID newUserId) {
        Team team = getTeamOrThrow(teamId);
        verifyOwner(team, requesterId);
        // 석수 초과 확인 (소유자 제외 — 소유자는 석수에 포함되지 않음)
        long currentMembers = memberRepository.countMembersExcludingOwner(teamId);
        if (team.getTotalSeats() != Integer.MAX_VALUE && currentMembers >= team.getTotalSeats()) {
            throw new IllegalStateException("팀 석수(" + team.getTotalSeats() + "석)가 가득 찼습니다.");
        }
        if (memberRepository.findByTeamIdAndUserId(teamId, newUserId).isPresent()) {
            throw new IllegalStateException("이미 팀 멤버입니다.");
        }
        return memberRepository.save(TeamMember.add(teamId, newUserId, TeamRole.MEMBER));
    }

    // 팀원 제거
    @Transactional
    public void removeMember(UUID teamId, UUID requesterId, UUID targetUserId) {
        Team team = getTeamOrThrow(teamId);
        verifyOwner(team, requesterId);
        if (targetUserId.equals(team.getOwnerUserId())) throw new IllegalStateException("팀장은 제거할 수 없습니다.");
        memberRepository.findByTeamIdAndUserId(teamId, targetUserId)
                .ifPresent(m -> memberRepository.deleteById(m.getId()));
    }

    // 프로젝트 석수 배분 설정 (0이면 제거)
    @Transactional
    public void allocateSeats(UUID teamId, UUID requesterId, UUID projectId, int seats) {
        Team team = getTeamOrThrow(teamId);
        verifyOwner(team, requesterId);
        if (seats < 0) throw new IllegalArgumentException("석수는 0 이상이어야 합니다.");

        int currentTotal = allocationRepository.sumAllocatedSeatsByTeamId(teamId);
        int existing = allocationRepository.findByTeamIdAndProjectId(teamId, projectId)
                .map(TeamProjectAllocation::getAllocatedSeats).orElse(0);
        int newTotal = currentTotal - existing + seats;

        if (team.getTotalSeats() != Integer.MAX_VALUE && newTotal > team.getTotalSeats()) {
            throw new IllegalStateException(
                "총 석수(" + team.getTotalSeats() + "석)를 초과합니다. 현재 배분 합계: " + newTotal);
        }

        if (seats == 0) {
            allocationRepository.findByTeamIdAndProjectId(teamId, projectId)
                    .ifPresent(a -> allocationRepository.deleteById(a.getId()));
            return;
        }
        var allocation = allocationRepository.findByTeamIdAndProjectId(teamId, projectId)
                .orElse(TeamProjectAllocation.allocate(teamId, projectId, 0));
        allocation.updateSeats(seats);
        allocationRepository.save(allocation);
    }

    // 팀 좌석 수 감소 — 증가는 결제가 필요하므로 /api/teams/{id}/seats/payment/prepare 경유
    @Transactional
    public Team decreaseSeats(UUID teamId, UUID requesterId, int seats) {
        Team team = getTeamOrThrow(teamId);
        verifyOwner(team, requesterId);
        if (seats > team.getTotalSeats()) {
            throw new IllegalStateException("좌석 증가는 결제가 필요합니다. 좌석 증가 결제를 이용해주세요.");
        }
        team.upgradePlan(UserPlan.DESKTOP, seats);
        return teamRepository.save(team);
    }

    // 내 팀 목록 조회
    public List<Team> getMyTeams(UUID userId) {
        return teamRepository.findByOwnerUserId(userId);
    }

    // 팀 멤버 목록 조회
    public List<TeamMember> getMembers(UUID teamId) {
        return memberRepository.findByTeamId(teamId);
    }

    // 팀 석수 배분 현황 조회
    public List<TeamProjectAllocation> getAllocations(UUID teamId) {
        return allocationRepository.findByTeamId(teamId);
    }

    private Team getTeamOrThrow(UUID teamId) {
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new IllegalArgumentException("팀을 찾을 수 없습니다: " + teamId));
    }

    private void verifyOwner(Team team, UUID userId) {
        if (!team.getOwnerUserId().equals(userId)) {
            throw new SecurityException("팀장만 수행할 수 있습니다.");
        }
    }
}
