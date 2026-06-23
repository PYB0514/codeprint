// ProjectLimit 경계 조건 단위 테스트 — 플랜별 프로젝트 수 제한 회귀 방지
package com.codeprint.domain.project;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectLimitTest {

    @Test
    @DisplayName("isExceeded — 현재 수가 제한 미만이면 false")
    void notExceeded_whenBelowLimit() {
        ProjectLimit limit = ProjectLimit.of(3);
        assertThat(limit.isExceeded(2)).isFalse();
    }

    @Test
    @DisplayName("isExceeded — 현재 수가 제한과 같으면 true (경계값, >= 이므로 더 못 만듦)")
    void exceeded_whenEqualToLimit() {
        ProjectLimit limit = ProjectLimit.of(3);
        assertThat(limit.isExceeded(3)).isTrue();
    }

    @Test
    @DisplayName("isExceeded — 현재 수가 제한을 넘으면 true")
    void exceeded_whenAboveLimit() {
        ProjectLimit limit = ProjectLimit.of(3);
        assertThat(limit.isExceeded(4)).isTrue();
    }

    @Test
    @DisplayName("isExceeded — 0개 제한이면 0개에서 이미 초과 (생성 불가)")
    void exceeded_whenZeroLimit() {
        ProjectLimit limit = ProjectLimit.of(0);
        assertThat(limit.isExceeded(0)).isTrue();
    }

    @Test
    @DisplayName("of — maxCount 값을 그대로 보유")
    void of_holdsMaxCount() {
        assertThat(ProjectLimit.of(7).maxCount()).isEqualTo(7);
    }
}
