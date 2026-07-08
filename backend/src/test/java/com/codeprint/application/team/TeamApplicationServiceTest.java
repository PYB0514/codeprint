// TeamApplicationService 단위 테스트 — 좌석 제한(경계)·석수 배분 총합·소유권 검증 회귀 방지
package com.codeprint.application.team;

import com.codeprint.domain.team.*;
import com.codeprint.shared.plan.UserPlan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeamApplicationServiceTest {

    @Mock private TeamRepository teamRepository;
    @Mock private TeamMemberRepository memberRepository;
    @Mock private TeamProjectAllocationRepository allocationRepository;

    private TeamApplicationService service() {
        return new TeamApplicationService(teamRepository, memberRepository, allocationRepository);
    }

    // --- deleteTeam: 소유권 검증 ---

    @Test
    @DisplayName("deleteTeam — 소유자면 삭제")
    void deleteTeam_success() {
        UUID owner = UUID.randomUUID();
        Team team = Team.create(owner, "t", UserPlan.DESKTOP, 15);
        when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));

        service().deleteTeam(team.getId(), owner);

        verify(teamRepository).deleteById(team.getId());
    }

    @Test
    @DisplayName("deleteTeam — 소유자가 아니면 SecurityException, 삭제 안 함")
    void deleteTeam_notOwner_rejected() {
        UUID owner = UUID.randomUUID();
        Team team = Team.create(owner, "t", UserPlan.DESKTOP, 15);
        when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));

        assertThatThrownBy(() -> service().deleteTeam(team.getId(), UUID.randomUUID()))
                .isInstanceOf(SecurityException.class);
        verify(teamRepository, never()).deleteById(any());
    }

    // --- addMember: 좌석 제한 경계 + 소유권 + 중복 ---

    @Test
    @DisplayName("addMember — 소유자 + 좌석 여유 + 비중복이면 멤버 추가")
    void addMember_success() {
        UUID owner = UUID.randomUUID();
        Team team = Team.create(owner, "t", UserPlan.DESKTOP, 15); // 15석
        when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));
        when(memberRepository.countMembersExcludingOwner(team.getId())).thenReturn(10L);
        when(memberRepository.findByTeamIdAndUserId(eq(team.getId()), any())).thenReturn(Optional.empty());
        when(memberRepository.save(any(TeamMember.class))).thenAnswer(inv -> inv.getArgument(0));

        TeamMember m = service().addMember(team.getId(), owner, UUID.randomUUID());

        assertThat(m).isNotNull();
        verify(memberRepository).save(any(TeamMember.class));
    }

    @Test
    @DisplayName("addMember — 소유자가 아니면 SecurityException, 저장 안 함")
    void addMember_notOwner_rejected() {
        UUID owner = UUID.randomUUID();
        Team team = Team.create(owner, "t", UserPlan.DESKTOP, 15);
        when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));

        assertThatThrownBy(() -> service().addMember(team.getId(), UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(SecurityException.class);
        verify(memberRepository, never()).save(any());
    }

    @Test
    @DisplayName("addMember — 좌석이 가득 차면(현재=총석수) IllegalStateException (경계값)")
    void addMember_seatsFull_rejected() {
        UUID owner = UUID.randomUUID();
        Team team = Team.create(owner, "t", UserPlan.DESKTOP, 15); // 15석
        when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));
        when(memberRepository.countMembersExcludingOwner(team.getId())).thenReturn(15L);

        assertThatThrownBy(() -> service().addMember(team.getId(), owner, UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("가득");
        verify(memberRepository, never()).save(any());
    }

    @Test
    @DisplayName("addMember — 좌석 경계 바로 아래(현재=총석수-1)면 추가 허용")
    void addMember_justUnderLimit_allowed() {
        UUID owner = UUID.randomUUID();
        Team team = Team.create(owner, "t", UserPlan.DESKTOP, 15); // 15석
        when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));
        when(memberRepository.countMembersExcludingOwner(team.getId())).thenReturn(14L);
        when(memberRepository.findByTeamIdAndUserId(eq(team.getId()), any())).thenReturn(Optional.empty());
        when(memberRepository.save(any(TeamMember.class))).thenAnswer(inv -> inv.getArgument(0));

        service().addMember(team.getId(), owner, UUID.randomUUID());

        verify(memberRepository).save(any(TeamMember.class));
    }

    @Test
    @DisplayName("addMember — 총석수가 MAX_VALUE(무제한 좌석)면 멤버가 많아도 좌석 제한 우회")
    void addMember_businessUnlimited_allowed() {
        UUID owner = UUID.randomUUID();
        Team team = Team.create(owner, "t", UserPlan.DESKTOP, Integer.MAX_VALUE); // MAX_VALUE석
        when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));
        when(memberRepository.countMembersExcludingOwner(team.getId())).thenReturn(1000L); // 멤버가 많아도
        when(memberRepository.findByTeamIdAndUserId(eq(team.getId()), any())).thenReturn(Optional.empty());
        when(memberRepository.save(any(TeamMember.class))).thenAnswer(inv -> inv.getArgument(0));

        service().addMember(team.getId(), owner, UUID.randomUUID());

        // 총석수가 MAX_VALUE이므로 현재 멤버가 많아도 제한 우회 → 추가 허용
        verify(memberRepository).save(any(TeamMember.class));
    }

    @Test
    @DisplayName("addMember — 이미 멤버이면 IllegalStateException")
    void addMember_duplicate_rejected() {
        UUID owner = UUID.randomUUID();
        UUID newUser = UUID.randomUUID();
        Team team = Team.create(owner, "t", UserPlan.DESKTOP, 15);
        when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));
        when(memberRepository.countMembersExcludingOwner(team.getId())).thenReturn(5L);
        when(memberRepository.findByTeamIdAndUserId(team.getId(), newUser))
                .thenReturn(Optional.of(TeamMember.add(team.getId(), newUser, TeamRole.MEMBER)));

        assertThatThrownBy(() -> service().addMember(team.getId(), owner, newUser))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 팀 멤버");
        verify(memberRepository, never()).save(any());
    }

    @Test
    @DisplayName("addMember — 존재하지 않는 팀이면 IllegalArgumentException")
    void addMember_teamNotFound_rejected() {
        UUID teamId = UUID.randomUUID();
        when(teamRepository.findById(teamId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().addMember(teamId, UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("팀을 찾을 수 없습니다");
    }

    // --- removeMember: 소유권 + 소유자 보호 ---

    @Test
    @DisplayName("removeMember — 소유자가 멤버 제거 시 삭제")
    void removeMember_success() {
        UUID owner = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        Team team = Team.create(owner, "t", UserPlan.DESKTOP, 15);
        TeamMember member = TeamMember.add(team.getId(), target, TeamRole.MEMBER);
        when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));
        when(memberRepository.findByTeamIdAndUserId(team.getId(), target)).thenReturn(Optional.of(member));

        service().removeMember(team.getId(), owner, target);

        verify(memberRepository).deleteById(member.getId());
    }

    @Test
    @DisplayName("removeMember — 소유자가 아니면 SecurityException, 삭제 안 함")
    void removeMember_notOwner_rejected() {
        UUID owner = UUID.randomUUID();
        Team team = Team.create(owner, "t", UserPlan.DESKTOP, 15);
        when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));

        assertThatThrownBy(() -> service().removeMember(team.getId(), UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(SecurityException.class);
        verify(memberRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("removeMember — 팀장 본인은 제거 불가 (IllegalStateException)")
    void removeMember_owner_rejected() {
        UUID owner = UUID.randomUUID();
        Team team = Team.create(owner, "t", UserPlan.DESKTOP, 15);
        when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));

        assertThatThrownBy(() -> service().removeMember(team.getId(), owner, owner))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("팀장은 제거할 수 없습니다");
        verify(memberRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("removeMember — 대상이 멤버가 아니면 no-op (예외 없음)")
    void removeMember_notMember_noop() {
        UUID owner = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        Team team = Team.create(owner, "t", UserPlan.DESKTOP, 15);
        when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));
        when(memberRepository.findByTeamIdAndUserId(team.getId(), target)).thenReturn(Optional.empty());

        service().removeMember(team.getId(), owner, target);

        verify(memberRepository, never()).deleteById(any());
    }

    // --- allocateSeats: 배분 총합 제한 경계 + 소유권 + 음수 ---

    @Test
    @DisplayName("allocateSeats — 소유자 + 총석수 이내면 신규 배분 저장")
    void allocateSeats_newWithinTotal() {
        UUID owner = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Team team = Team.create(owner, "t", UserPlan.DESKTOP, 15); // 15석
        when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));
        when(allocationRepository.sumAllocatedSeatsByTeamId(team.getId())).thenReturn(0);
        when(allocationRepository.findByTeamIdAndProjectId(team.getId(), projectId)).thenReturn(Optional.empty());

        service().allocateSeats(team.getId(), owner, projectId, 5);

        verify(allocationRepository).save(any(TeamProjectAllocation.class));
    }

    @Test
    @DisplayName("allocateSeats — 소유자가 아니면 SecurityException")
    void allocateSeats_notOwner_rejected() {
        UUID owner = UUID.randomUUID();
        Team team = Team.create(owner, "t", UserPlan.DESKTOP, 15);
        when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));

        assertThatThrownBy(() -> service().allocateSeats(team.getId(), UUID.randomUUID(), UUID.randomUUID(), 5))
                .isInstanceOf(SecurityException.class);
        verify(allocationRepository, never()).save(any());
    }

    @Test
    @DisplayName("allocateSeats — 음수 석수는 IllegalArgumentException")
    void allocateSeats_negative_rejected() {
        UUID owner = UUID.randomUUID();
        Team team = Team.create(owner, "t", UserPlan.DESKTOP, 15);
        when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));

        assertThatThrownBy(() -> service().allocateSeats(team.getId(), owner, UUID.randomUUID(), -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("석수는 0 이상");
        verify(allocationRepository, never()).save(any());
    }

    @Test
    @DisplayName("allocateSeats — 배분 합계가 총석수를 초과하면 IllegalStateException")
    void allocateSeats_exceedsTotal_rejected() {
        UUID owner = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Team team = Team.create(owner, "t", UserPlan.DESKTOP, 15); // 15석
        when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));
        when(allocationRepository.sumAllocatedSeatsByTeamId(team.getId())).thenReturn(14); // 기존 14
        when(allocationRepository.findByTeamIdAndProjectId(team.getId(), projectId)).thenReturn(Optional.empty());

        // newTotal = 14 - 0 + 5 = 19 > 15
        assertThatThrownBy(() -> service().allocateSeats(team.getId(), owner, projectId, 5))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("초과");
        verify(allocationRepository, never()).save(any());
    }

    @Test
    @DisplayName("allocateSeats — 기존 배분 교체 시 자기 배분은 합계에서 제외하고 검사")
    void allocateSeats_reallocateExcludesOwnExisting() {
        UUID owner = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Team team = Team.create(owner, "t", UserPlan.DESKTOP, 15); // 15석
        TeamProjectAllocation existing = TeamProjectAllocation.allocate(team.getId(), projectId, 10);
        when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));
        when(allocationRepository.sumAllocatedSeatsByTeamId(team.getId())).thenReturn(15); // 전체 15 (이 프로젝트 10 포함)
        when(allocationRepository.findByTeamIdAndProjectId(team.getId(), projectId)).thenReturn(Optional.of(existing));

        // newTotal = 15 - 10 + 12 = 17 > 15 이면 초과... 12로 하면 17 초과. 8로 하면 15-10+8=13 ≤ 15 허용
        service().allocateSeats(team.getId(), owner, projectId, 8);

        assertThat(existing.getAllocatedSeats()).isEqualTo(8);
        verify(allocationRepository).save(existing);
    }

    @Test
    @DisplayName("allocateSeats — 0석이면 기존 배분 삭제")
    void allocateSeats_zeroDeletes() {
        UUID owner = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Team team = Team.create(owner, "t", UserPlan.DESKTOP, 15);
        TeamProjectAllocation existing = TeamProjectAllocation.allocate(team.getId(), projectId, 5);
        when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));
        when(allocationRepository.sumAllocatedSeatsByTeamId(team.getId())).thenReturn(5);
        when(allocationRepository.findByTeamIdAndProjectId(team.getId(), projectId)).thenReturn(Optional.of(existing));

        service().allocateSeats(team.getId(), owner, projectId, 0);

        verify(allocationRepository).deleteById(existing.getId());
        verify(allocationRepository, never()).save(any());
    }

    // --- getMembers/getAllocations: 소유권 검증 (IDOR 방지) ---

    @Test
    @DisplayName("getMembers — 소유자면 멤버 목록 반환")
    void getMembers_owner_returnsMembers() {
        UUID owner = UUID.randomUUID();
        Team team = Team.create(owner, "t", UserPlan.DESKTOP, 15);
        TeamMember member = TeamMember.add(team.getId(), UUID.randomUUID(), TeamRole.MEMBER);
        when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));
        when(memberRepository.findByTeamId(team.getId())).thenReturn(java.util.List.of(member));

        assertThat(service().getMembers(team.getId(), owner)).containsExactly(member);
    }

    @Test
    @DisplayName("getMembers — 소유자가 아니면 SecurityException, 조회 안 함(타 팀 멤버 열람 IDOR 차단)")
    void getMembers_notOwner_rejected() {
        UUID owner = UUID.randomUUID();
        Team team = Team.create(owner, "t", UserPlan.DESKTOP, 15);
        when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));

        assertThatThrownBy(() -> service().getMembers(team.getId(), UUID.randomUUID()))
                .isInstanceOf(SecurityException.class);
        verify(memberRepository, never()).findByTeamId(any());
    }

    @Test
    @DisplayName("getAllocations — 소유자면 배분 현황 반환")
    void getAllocations_owner_returnsAllocations() {
        UUID owner = UUID.randomUUID();
        Team team = Team.create(owner, "t", UserPlan.DESKTOP, 15);
        TeamProjectAllocation allocation = TeamProjectAllocation.allocate(team.getId(), UUID.randomUUID(), 5);
        when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));
        when(allocationRepository.findByTeamId(team.getId())).thenReturn(java.util.List.of(allocation));

        assertThat(service().getAllocations(team.getId(), owner)).containsExactly(allocation);
    }

    @Test
    @DisplayName("getAllocations — 소유자가 아니면 SecurityException, 조회 안 함(타 팀 좌석배분 열람 IDOR 차단)")
    void getAllocations_notOwner_rejected() {
        UUID owner = UUID.randomUUID();
        Team team = Team.create(owner, "t", UserPlan.DESKTOP, 15);
        when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));

        assertThatThrownBy(() -> service().getAllocations(team.getId(), UUID.randomUUID()))
                .isInstanceOf(SecurityException.class);
        verify(allocationRepository, never()).findByTeamId(any());
    }

    // --- decreaseSeats: 소유권 검증 + 증가 거부 ---

    @Test
    @DisplayName("decreaseSeats — 소유자면 총석수를 전달받은 값(감소)으로 갱신")
    void decreaseSeats_success() {
        UUID owner = UUID.randomUUID();
        Team team = Team.create(owner, "t", UserPlan.DESKTOP, 15); // 15석
        when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));
        when(teamRepository.save(team)).thenReturn(team);

        Team result = service().decreaseSeats(team.getId(), owner, 10);

        assertThat(result.getTotalSeats()).isEqualTo(10);
        verify(teamRepository).save(team);
    }

    @Test
    @DisplayName("decreaseSeats — 소유자가 아니면 SecurityException")
    void decreaseSeats_notOwner_rejected() {
        UUID owner = UUID.randomUUID();
        Team team = Team.create(owner, "t", UserPlan.DESKTOP, 15);
        when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));

        assertThatThrownBy(() -> service().decreaseSeats(team.getId(), UUID.randomUUID(), 10))
                .isInstanceOf(SecurityException.class);
        verify(teamRepository, never()).save(any());
    }

    @Test
    @DisplayName("decreaseSeats — 현재보다 많은 석수(증가)는 IllegalStateException, 결제 경유 안내")
    void decreaseSeats_increaseAttempt_rejected() {
        UUID owner = UUID.randomUUID();
        Team team = Team.create(owner, "t", UserPlan.DESKTOP, 15);
        when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));

        assertThatThrownBy(() -> service().decreaseSeats(team.getId(), owner, 20))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("결제가 필요");
        verify(teamRepository, never()).save(any());
    }
}
