// 플랜별 프로젝트 생성 제한 Value Object
package com.codeprint.domain.project;

import com.codeprint.domain.user.UserPlan;

public record ProjectLimit(int maxCount) {

    public static ProjectLimit of(UserPlan plan) {
        return new ProjectLimit(plan.maxProjects());
    }

    public boolean isExceeded(int currentCount) {
        return currentCount >= maxCount;
    }
}
