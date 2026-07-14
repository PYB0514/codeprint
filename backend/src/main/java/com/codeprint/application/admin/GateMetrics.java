// 지표 대시보드 4층 체계(북극성·경험·실적·가드레일) 집계 결과 — 값 객체
package com.codeprint.application.admin;

public record GateMetrics(
        int guardedRepos,           // 북극성 — 최근 30일 내 게이트 체크가 발생한 프로젝트 수
        int weeklyNewAnalysisRepos, // 경험 — 최근 7일 내 신규 분석이 발생한 프로젝트 수
        int weeklyShares,           // 경험 — 최근 7일 내 공유(게시글) 수
        int blockedPrsTotal,        // 실적 — 게이트가 failure 처리한 PR 누적 수
        int highWarningPrecisionPct // 가드레일 — 최근 30일 HIGH 경고 발생 대비 오탐 미신고 비율(%), HIGH 발생 0건이면 100
) {}
