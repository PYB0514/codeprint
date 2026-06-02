// 사용자 플랜 열거형 (FREE / PRO)
package com.codeprint.domain.user;

public enum UserPlan {
    FREE, PRO;

    // 플랜별 최대 프로젝트 수 반환
    public int maxProjects() {
        return switch (this) {
            case FREE -> 3;
            case PRO -> Integer.MAX_VALUE;
        };
    }
}
