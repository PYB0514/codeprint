// Team 엔티티 단위 테스트 — 명시적 석수 설정·플랜 업그레이드 전이 회귀 방지
package com.codeprint.domain.team;

import com.codeprint.shared.plan.UserPlan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TeamTest {

    private final UUID ownerId = UUID.randomUUID();

    @Test
    @DisplayName("create — 전달받은 seats 값으로 totalSeats 설정")
    void create_setsSeatsFromParameter() {
        Team team = Team.create(ownerId, "팀", UserPlan.DESKTOP, 15);

        assertThat(team.getId()).isNotNull();
        assertThat(team.getOwnerUserId()).isEqualTo(ownerId);
        assertThat(team.getPlan()).isEqualTo(UserPlan.DESKTOP);
        assertThat(team.getTotalSeats()).isEqualTo(15);
        assertThat(team.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("upgradePlan — 플랜과 totalSeats를 전달받은 값으로 갱신")
    void upgradePlan_updatesPlanAndSeats() {
        Team team = Team.create(ownerId, "팀", UserPlan.DESKTOP, 15);

        team.upgradePlan(UserPlan.DESKTOP, 40);

        assertThat(team.getPlan()).isEqualTo(UserPlan.DESKTOP);
        assertThat(team.getTotalSeats()).isEqualTo(40);
    }
}
