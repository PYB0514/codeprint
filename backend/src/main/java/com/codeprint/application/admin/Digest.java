// 일일 다이제스트 — 하루치 지표 + 감지된 이상 신호 목록
package com.codeprint.application.admin;

import java.time.LocalDate;
import java.util.List;

public record Digest(
        LocalDate date,
        DailyMetrics metrics,
        int openFeedback,    // 현재 미처리 문의 누적 (시점 게이지 — 일별 스냅샷 아님)
        List<String> anomalies
) {
    // 이상 신호 존재 여부
    public boolean hasAnomaly() {
        return !anomalies.isEmpty();
    }
}
