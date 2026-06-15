// User 도메인 엔티티 단위 테스트 — 상태 전이 및 역할 관리 회귀 방지
package com.codeprint.domain.user;

import com.codeprint.shared.plan.UserPlan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    @Test
    @DisplayName("create() — 기본값: FREE 플랜, USER 역할, enabled=true")
    void create_defaultState() {
        User user = User.create(12345L, "test@github.com", "testuser");

        assertThat(user.getPlan()).isEqualTo(UserPlan.FREE);
        assertThat(user.getRole()).isEqualTo(UserRole.USER);
        assertThat(user.isEnabled()).isTrue();
        assertThat(user.getId()).isNotNull();
        assertThat(user.getGithubId()).isEqualTo(12345L);
        assertThat(user.getEmail()).isEqualTo("test@github.com");
        assertThat(user.getUsername()).isEqualTo("testuser");
        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(user.getGithubAccessToken()).isNull();
    }

    @Test
    @DisplayName("upgradeToPro() — 플랜이 PRO로 변경")
    void upgradeToPro_changesPlanToPro() {
        User user = User.create(1L, "a@github.com", "a");

        user.upgradeToPro();

        assertThat(user.getPlan()).isEqualTo(UserPlan.PRO);
        assertThat(user.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("downgradeToFree() — 플랜이 FREE로 변경")
    void downgradeToFree_changesPlanToFree() {
        User user = User.create(1L, "a@github.com", "a");
        user.upgradeToPro();

        user.downgradeToFree();

        assertThat(user.getPlan()).isEqualTo(UserPlan.FREE);
    }

    @Test
    @DisplayName("disable() — enabled=false")
    void disable_setsEnabledFalse() {
        User user = User.create(1L, "a@github.com", "a");

        user.disable();

        assertThat(user.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("enable() — disabled 상태에서 enabled=true 복구")
    void enable_restoresEnabled() {
        User user = User.create(1L, "a@github.com", "a");
        user.disable();

        user.enable();

        assertThat(user.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("updateGithubAccessToken() — 토큰 갱신")
    void updateGithubAccessToken_setsToken() {
        User user = User.create(1L, "a@github.com", "a");

        user.updateGithubAccessToken("gho_newtoken");

        assertThat(user.getGithubAccessToken()).isEqualTo("gho_newtoken");
        assertThat(user.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("recordActivity() — 최초 활동: lastActiveAt 갱신 + true 반환")
    void recordActivity_firstTime_updates() {
        User user = User.create(1L, "a@github.com", "a");
        Instant now = Instant.now();

        boolean updated = user.recordActivity(now);

        assertThat(updated).isTrue();
        assertThat(user.getLastActiveAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("recordActivity() — 쓰로틀 이내 재호출: no-op + false 반환")
    void recordActivity_withinThrottle_noOp() {
        User user = User.create(1L, "a@github.com", "a");
        Instant first = Instant.now();
        user.recordActivity(first);

        boolean updated = user.recordActivity(first.plus(5, ChronoUnit.MINUTES));

        assertThat(updated).isFalse();
        assertThat(user.getLastActiveAt()).isEqualTo(first);
    }

    @Test
    @DisplayName("recordActivity() — 쓰로틀 초과 재호출: lastActiveAt 갱신 + true 반환")
    void recordActivity_afterThrottle_updates() {
        User user = User.create(1L, "a@github.com", "a");
        Instant first = Instant.now();
        user.recordActivity(first);
        Instant later = first.plus(11, ChronoUnit.MINUTES);

        boolean updated = user.recordActivity(later);

        assertThat(updated).isTrue();
        assertThat(user.getLastActiveAt()).isEqualTo(later);
    }

    @Test
    @DisplayName("recordActivity() — 활동 기록은 updatedAt을 건드리지 않음 (프로필 변경 시각과 분리)")
    void recordActivity_doesNotTouchUpdatedAt() {
        User user = User.create(1L, "a@github.com", "a");
        Instant before = user.getUpdatedAt();

        user.recordActivity(Instant.now().plus(1, ChronoUnit.HOURS));

        assertThat(user.getUpdatedAt()).isEqualTo(before);
    }
}
