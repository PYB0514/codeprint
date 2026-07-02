// 구독 플랜 열거형 (FREE / DESKTOP) — user·team·project 공유 어휘 (Shared Kernel)
package com.codeprint.shared.plan;

public enum UserPlan {
    FREE, DESKTOP;

    // Desktop 라이센스(개인/팀 공통) 보유 여부
    public boolean isPaid() {
        return this == DESKTOP;
    }

    // 좌석당 월 요금(원). 팀은 seats × 이 값으로 계산
    public int monthlyPricePerSeat() {
        return switch (this) {
            case FREE -> 0;
            case DESKTOP -> 4_900;
        };
    }
}
