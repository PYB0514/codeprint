// UserPlan 단위 테스트 — Desktop 라이센스 요금 계산 회귀 방지
package com.codeprint.domain.user;

import com.codeprint.shared.plan.UserPlan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserPlanTest {

    @Test
    @DisplayName("isPaid — FREE는 false, DESKTOP은 true")
    void isPaid_onlyDesktopTrue() {
        assertThat(UserPlan.FREE.isPaid()).isFalse();
        assertThat(UserPlan.DESKTOP.isPaid()).isTrue();
    }

    @Test
    @DisplayName("monthlyPricePerSeat — FREE=0원, DESKTOP=4,900원")
    void monthlyPricePerSeat_perPlan() {
        assertThat(UserPlan.FREE.monthlyPricePerSeat()).isEqualTo(0);
        assertThat(UserPlan.DESKTOP.monthlyPricePerSeat()).isEqualTo(4_900);
    }
}
