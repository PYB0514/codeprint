// 레이트리밋 트립(429) 카테고리별 집계 — 일일 다이제스트 이상탐지 신호 제공
package com.codeprint.infrastructure.config;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

@Component
public class RateLimitMetrics {

    private final Map<String, LongAdder> trips = new ConcurrentHashMap<>();

    // 429 발생 시 카테고리별 카운트 증가
    public void recordTrip(String category) {
        trips.computeIfAbsent(category, k -> new LongAdder()).increment();
    }

    // 현재까지 집계를 반환하고 카운트를 0으로 리셋 — 다이제스트 주기(일 단위)에 맞춰 호출
    public Map<String, Long> snapshotAndReset() {
        Map<String, Long> snapshot = new ConcurrentHashMap<>();
        trips.forEach((category, adder) -> {
            long count = adder.sumThenReset();
            if (count > 0) snapshot.put(category, count);
        });
        return snapshot;
    }
}
