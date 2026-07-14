// 일일 다이제스트 — 하루치 지표 + 감지된 이상 신호 목록
package com.codeprint.application.admin;

import java.time.LocalDate;
import java.util.List;

public record Digest(
        LocalDate date,
        DailyMetrics metrics,
        int openFeedback,    // 현재 미처리 문의 누적 (시점 게이지 — 일별 스냅샷 아님)
        long dbSizeBytes,    // 현재 DB 총 크기 (시점 게이지 — Railway 디스크 풀 사고[G-4] 재발방지 지표)
        List<TableSize> topTables, // 크기 상위 테이블 목록 (시점 게이지, 원인 테이블 파악용)
        List<String> anomalies
) {
    // 이상 신호 존재 여부
    public boolean hasAnomaly() {
        return !anomalies.isEmpty();
    }

    // 테이블명 + 바이트 크기
    public record TableSize(String name, long bytes) {}
}
