// Team 엔티티 단위 테스트 — 플랜 기반 석수 산정·플랜 업그레이드 전이 회귀 방지
package com.codeprint.domain.team;

import com.codeprint.shared.plan.UserPlan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TeamTest {

    private final UUID ownerId = UUID.randomUUID();

    @Test
    @DisplayName("create — 플랜 기본 석수로 totalSeats 설정 (TEAM_STARTER=15)")
    void create_setsSeatsFromPlan() {
        Team team = Team.create(ownerId, "팀", UserPlan.TEAM_STARTER);

        assertThat(team.getId()).isNotNull();
        assertThat(team.getOwnerUserId()).isEqualTo(ownerId);
        assertThat(team.getPlan()).isEqualTo(UserPlan.TEAM_STARTER);
        assertThat(team.getTotalSeats()).isEqualTo(15);
        assertThat(team.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("upgradePlan — 플랜과 totalSeats를 새 플랜 기준으로 갱신")
    void upgradePlan_updatesPlanAndSeats() {
        Team team = Team.create(ownerId, "팀", UserPlan.TEAM_STARTER);

        team.upgradePlan(UserPlan.TEAM_GROWTH);

        assertThat(team.getPlan()).isEqualTo(UserPlan.TEAM_GROWTH);
        assertThat(team.getTotalSeats()).isEqualTo(40);
    }
}
