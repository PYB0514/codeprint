// TeamApiKeyApplicationService 단위 테스트 — 소유권 검증·타 팀 키 조작 차단 회귀 방지
package com.codeprint.application.team;

import com.codeprint.domain.team.Team;
import com.codeprint.domain.team.TeamApiKey;
import com.codeprint.domain.team.TeamApiKeyRepository;
import com.codeprint.domain.team.TeamRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeamApiKeyApplicationServiceTest {

    @Mock private TeamRepository teamRepository;
    @Mock private TeamApiKeyRepository apiKeyRepository;

    private TeamApiKeyApplicationService service() {
        return new TeamApiKeyApplicationService(teamRepository, apiKeyRepository);
    }

    @Test
    @DisplayName("issueKey — 소유자면 발급하고 저장")
    void issueKey_owner_success() {
        UUID owner = UUID.randomUUID();
        Team team = Team.create(owner, "t", UserPlan.DESKTOP, 15);
        when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));
        when(apiKeyRepository.save(any(TeamApiKey.class))).thenAnswer(inv -> inv.getArgument(0));

        TeamApiKey.IssuedKey issued = service().issueKey(team.getId(), owner, "ci-agent");

        assertThat(issued.rawKey()).startsWith("cpk_");
        verify(apiKeyRepository).save(any(TeamApiKey.class));
    }

    @Test
    @DisplayName("issueKey — 소유자가 아니면 SecurityException, 저장 안 함")
    void issueKey_notOwner_rejected() {
        UUID owner = UUID.randomUUID();
        Team team = Team.create(owner, "t", UserPlan.DESKTOP, 15);
        when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));

        assertThatThrownBy(() -> service().issueKey(team.getId(), UUID.randomUUID(), "x"))
                .isInstanceOf(SecurityException.class);
        verify(apiKeyRepository, never()).save(any());
    }

    @Test
    @DisplayName("getKeys — 소유자가 아니면 SecurityException, 조회 안 함(타 팀 키 목록 열람 IDOR 차단)")
    void getKeys_notOwner_rejected() {
        UUID owner = UUID.randomUUID();
        Team team = Team.create(owner, "t", UserPlan.DESKTOP, 15);
        when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));

        assertThatThrownBy(() -> service().getKeys(team.getId(), UUID.randomUUID()))
                .isInstanceOf(SecurityException.class);
        verify(apiKeyRepository, never()).findByTeamId(any());
    }

    @Test
    @DisplayName("revokeKey — 소유자면 폐기하고 저장")
    void revokeKey_owner_success() {
        UUID owner = UUID.randomUUID();
        Team team = Team.create(owner, "t", UserPlan.DESKTOP, 15);
        TeamApiKey.IssuedKey issued = TeamApiKey.issue(team.getId(), "ci-agent", owner);
        when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));
        when(apiKeyRepository.findById(issued.entity().getId())).thenReturn(Optional.of(issued.entity()));

        service().revokeKey(team.getId(), owner, issued.entity().getId());

        assertThat(issued.entity().isRevoked()).isTrue();
        verify(apiKeyRepository).save(issued.entity());
    }

    @Test
    @DisplayName("revokeKey — 다른 팀 소유의 키면 IllegalArgumentException(교차 팀 폐기 차단)")
    void revokeKey_wrongTeam_rejected() {
        UUID owner = UUID.randomUUID();
        Team team = Team.create(owner, "t", UserPlan.DESKTOP, 15);
        TeamApiKey.IssuedKey otherTeamKey = TeamApiKey.issue(UUID.randomUUID(), "other", owner);
        when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));
        when(apiKeyRepository.findById(otherTeamKey.entity().getId())).thenReturn(Optional.of(otherTeamKey.entity()));

        assertThatThrownBy(() -> service().revokeKey(team.getId(), owner, otherTeamKey.entity().getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이 팀의 API 키가 아닙니다");
        verify(apiKeyRepository, never()).save(any());
    }
}
