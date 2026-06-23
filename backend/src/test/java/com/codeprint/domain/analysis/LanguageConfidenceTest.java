// LanguageConfidence 경계 검증 단위 테스트 — 신뢰도 0~1 범위 규칙 회귀 방지
package com.codeprint.domain.analysis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LanguageConfidenceTest {

    @Test
    @DisplayName("of — 경계값 0.0과 1.0은 허용")
    void of_acceptsBoundaryValues() {
        assertThat(LanguageConfidence.of("java", 0.0).confidence()).isEqualTo(0.0);
        assertThat(LanguageConfidence.of("java", 1.0).confidence()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("of — 범위 내 값은 language·confidence 그대로 보유")
    void of_holdsValues() {
        LanguageConfidence lc = LanguageConfidence.of("python", 0.75);
        assertThat(lc.language()).isEqualTo("python");
        assertThat(lc.confidence()).isEqualTo(0.75);
    }

    @Test
    @DisplayName("of — 0 미만이면 IllegalArgumentException")
    void of_rejectsBelowZero() {
        assertThatThrownBy(() -> LanguageConfidence.of("java", -0.01))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("of — 1 초과면 IllegalArgumentException")
    void of_rejectsAboveOne() {
        assertThatThrownBy(() -> LanguageConfidence.of("java", 1.01))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
