// UserPlan 경계 조건 단위 테스트 — 프로젝트 수 제한 비즈니스 규칙 회귀 방지
package com.codeprint.domain.user;

import com.codeprint.shared.plan.UserPlan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserPlanTest {

    @Test
    @DisplayName("FREE 플랜 — maxProjects()=3")
    void free_maxProjectsIsThree() {
        assertThat(UserPlan.FREE.maxProjects()).isEqualTo(3);
    }

    @Test
    @DisplayName("PRO 플랜 — maxProjects()=Integer.MAX_VALUE")
    void pro_maxProjectsIsUnlimited() {
        assertThat(UserPlan.PRO.maxProjects()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("FREE 플랜 — 3개까지 허용, 4번째부터 초과")
    void free_projectLimitBoundary() {
        int limit = UserPlan.FREE.maxProjects();

        assertThat(0).isLessThanOrEqualTo(limit);     // 0개 허용
        assertThat(1).isLessThanOrEqualTo(limit);     // 1개 허용
        assertThat(3).isLessThanOrEqualTo(limit);     // 3개 허용 (경계값)
        assertThat(4).isGreaterThan(limit);            // 4개 초과
    }

    @Test
    @DisplayName("PRO 플랜 — 1000개도 허용")
    void pro_allowsLargeProjectCount() {
        int limit = UserPlan.PRO.maxProjects();
        assertThat(1000).isLessThanOrEqualTo(limit);
    }

    @Test
    @DisplayName("isTeamPlan — TEAM_* 만 true, FREE·PRO 는 false")
    void isTeamPlan_onlyTeamPlans() {
        assertThat(UserPlan.FREE.isTeamPlan()).isFalse();
        assertThat(UserPlan.PRO.isTeamPlan()).isFalse();
        assertThat(UserPlan.TEAM_STARTER.isTeamPlan()).isTrue();
        assertThat(UserPlan.TEAM_GROWTH.isTeamPlan()).isTrue();
        assertThat(UserPlan.TEAM_BUSINESS.isTeamPlan()).isTrue();
    }

    @Test
    @DisplayName("isPro — PRO·TEAM_* 는 true, FREE 만 false")
    void isPro_freeIsFalseRestTrue() {
        assertThat(UserPlan.FREE.isPro()).isFalse();
        assertThat(UserPlan.PRO.isPro()).isTrue();
        assertThat(UserPlan.TEAM_STARTER.isPro()).isTrue();
        assertThat(UserPlan.TEAM_GROWTH.isPro()).isTrue();
        assertThat(UserPlan.TEAM_BUSINESS.isPro()).isTrue();
    }

    @Test
    @DisplayName("defaultTotalSeats — 팀 플랜별 기본 석수, 비팀은 5")
    void defaultTotalSeats_perPlan() {
        assertThat(UserPlan.FREE.defaultTotalSeats()).isEqualTo(5);
        assertThat(UserPlan.PRO.defaultTotalSeats()).isEqualTo(5);
        assertThat(UserPlan.TEAM_STARTER.defaultTotalSeats()).isEqualTo(15);
        assertThat(UserPlan.TEAM_GROWTH.defaultTotalSeats()).isEqualTo(40);
        assertThat(UserPlan.TEAM_BUSINESS.defaultTotalSeats()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("monthlyPrice — 플랜별 월 요금")
    void monthlyPrice_perPlan() {
        assertThat(UserPlan.FREE.monthlyPrice()).isEqualTo(0);
        assertThat(UserPlan.PRO.monthlyPrice()).isEqualTo(9_900);
        assertThat(UserPlan.TEAM_STARTER.monthlyPrice()).isEqualTo(39_000);
        assertThat(UserPlan.TEAM_GROWTH.monthlyPrice()).isEqualTo(79_000);
        assertThat(UserPlan.TEAM_BUSINESS.monthlyPrice()).isEqualTo(149_000);
    }
}
