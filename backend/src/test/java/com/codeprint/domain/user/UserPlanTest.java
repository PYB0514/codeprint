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
}
