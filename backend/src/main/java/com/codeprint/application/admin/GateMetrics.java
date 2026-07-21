// 지표 대시보드 4층 체계(북극성·경험·실적·가드레일) 집계 결과 — 값 객체
package com.codeprint.application.admin;

public record GateMetrics(
        int guardedRepos,           // 북극성 — 최근 30일 내 게이트 체크가 발생한 프로젝트 수
        int weeklyNewAnalysisRepos, // 경험 — 최근 7일 내 신규 분석이 발생한 프로젝트 수
        int weeklyShares,           // 경험 — 최근 7일 내 공유(게시글) 수
        int blockedPrsTotal,        // 실적 — 게이트가 failure 처리한 PR 누적 수
        // 가드레일(참고) — 최근 30일 HIGH 경고 발생 대비 "사용자가 오탐으로 신고한" 비율(%), HIGH 발생 0건이면 100.
        // 헌장이 정의한 "벤치(oracle) 기준 정밀도"가 아니다 — 벤치 기반 precision은 아직 미측정(decisions/DECISIONS_BACKEND.md 참조).
        int fpReportRatePct
) {}
