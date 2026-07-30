// RateLimitMetrics 단위 테스트 — 카테고리별 트립 집계·스냅샷 후 리셋 검증
package com.codeprint.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitMetricsTest {

    @Test
    @DisplayName("recordTrip은 카테고리별로 독립 집계된다")
    void 카테고리별_독립_집계() {
        RateLimitMetrics metrics = new RateLimitMetrics();

        metrics.recordTrip("analysis");
        metrics.recordTrip("analysis");
        metrics.recordTrip("webhook-github");

        Map<String, Long> snapshot = metrics.snapshotAndReset();

        assertThat(snapshot).containsEntry("analysis", 2L).containsEntry("webhook-github", 1L);
    }

    @Test
    @DisplayName("snapshotAndReset 이후 카운트는 0으로 초기화된다")
    void 스냅샷_이후_리셋() {
        RateLimitMetrics metrics = new RateLimitMetrics();
        metrics.recordTrip("analysis");

        metrics.snapshotAndReset();
        Map<String, Long> second = metrics.snapshotAndReset();

        assertThat(second).doesNotContainKey("analysis");
    }

    @Test
    @DisplayName("트립이 없으면 빈 맵을 반환한다")
    void 트립_없으면_빈맵() {
        RateLimitMetrics metrics = new RateLimitMetrics();

        assertThat(metrics.snapshotAndReset()).isEmpty();
    }

    @Test
    @DisplayName("restore는 스냅샷으로 비운 카운트를 되돌린다(다이제스트 저장 실패 시 복구용)")
    void restore_유실된_카운트_복구() {
        RateLimitMetrics metrics = new RateLimitMetrics();
        metrics.recordTrip("analysis");
        metrics.recordTrip("analysis");
        Map<String, Long> snapshot = metrics.snapshotAndReset();

        metrics.restore(snapshot);

        assertThat(metrics.snapshotAndReset()).containsEntry("analysis", 2L);
    }

    @Test
    @DisplayName("restore 이후 새로 발생한 트립과 합산된다(복구가 이후 집계를 지우지 않음)")
    void restore_이후_신규트립과_합산() {
        RateLimitMetrics metrics = new RateLimitMetrics();
        metrics.recordTrip("analysis");
        Map<String, Long> snapshot = metrics.snapshotAndReset(); // {analysis: 1}

        metrics.restore(snapshot);
        metrics.recordTrip("analysis"); // 복구 후 발생한 신규 트립

        assertThat(metrics.snapshotAndReset()).containsEntry("analysis", 2L);
    }
}
