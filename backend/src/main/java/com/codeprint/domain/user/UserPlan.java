// 사용자 플랜 열거형 (FREE / PRO)
package com.codeprint.domain.user;

public enum UserPlan {
    FREE, PRO;

    public int maxProjects() {
        return switch (this) {
            case FREE -> 3;
            case PRO -> Integer.MAX_VALUE;
        };
    }
}
