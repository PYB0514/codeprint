// 구독 플랜 열거형 (FREE / PRO / TEAM_*) — user·team·project 공유 어휘 (Shared Kernel)
package com.codeprint.shared.plan;

public enum UserPlan {
    FREE, PRO, TEAM_STARTER, TEAM_GROWTH, TEAM_BUSINESS;

    // 플랜별 최대 프로젝트 수 반환
    public int maxProjects() {
        return switch (this) {
            case FREE -> 3;
            case PRO, TEAM_STARTER, TEAM_GROWTH, TEAM_BUSINESS -> Integer.MAX_VALUE;
        };
    }

    // 팀 플랜 여부 확인
    public boolean isTeamPlan() {
        return this == TEAM_STARTER || this == TEAM_GROWTH || this == TEAM_BUSINESS;
    }

    // 팀 플랜별 기본 총 석수 반환 (TEAM_BUSINESS는 별도 계약)
    public int defaultTotalSeats() {
        return switch (this) {
            case TEAM_STARTER  -> 15;
            case TEAM_GROWTH   -> 40;
            case TEAM_BUSINESS -> Integer.MAX_VALUE;
            default -> 5; // FREE: 소유자 제외 협업자 5명
        };
    }

    // PRO 이상 유료 기능 사용 가능 여부 (팀 유료 플랜 포함)
    public boolean isPro() {
        return this == PRO || isTeamPlan();
    }

    // 팀 플랜 월 요금 (원)
    public int monthlyPrice() {
        return switch (this) {
            case FREE          -> 0;
            case PRO           -> 9_900;
            case TEAM_STARTER  -> 39_000;
            case TEAM_GROWTH   -> 79_000;
            case TEAM_BUSINESS -> 149_000;
        };
    }
}
