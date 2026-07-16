// PR 게이트 셀프서비스 연결 상태 — 값 객체
package com.codeprint.application.analysis;

import java.time.Instant;

public record PrGateStatus(
        boolean connected,
        String secret,       // 연결 안 됐으면 null
        LastCheck lastCheck  // 아직 체크 이력 없으면 null
) {
    // 가장 최근 게이트 체크 결과 요약
    public record LastCheck(int prNumber, String state, int highCount, int warningCount, Instant checkedAt) {}
}
