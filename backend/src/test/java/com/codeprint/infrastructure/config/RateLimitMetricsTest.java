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
}
