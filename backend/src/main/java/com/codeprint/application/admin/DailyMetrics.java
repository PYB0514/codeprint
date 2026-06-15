// 하루치 원시 지표 — 집계 쿼리 결과를 담는 값 객체 (다이제스트 계산 입력)
package com.codeprint.application.admin;

public record DailyMetrics(
        int newUsers,         // 신규 가입 수
        int activeUsers,      // 활성 사용자(DAU) — 구간 내 마지막 활동이 기록된 사용자 수
        int newProjects,      // 신규 프로젝트 수
        int analysesTotal,    // 분석 실행 수
        int analysesFailed,   // 실패한 분석 수
        int paymentsCount,    // 확정 결제 건수
        long paymentsAmount,  // 확정 결제 금액 합 (원)
        int newFeedback       // 신규 문의 수
) {}
