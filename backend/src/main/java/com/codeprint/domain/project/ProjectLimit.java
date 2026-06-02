// 플랜별 프로젝트 생성 제한 Value Object
package com.codeprint.domain.project;

import com.codeprint.domain.user.UserPlan;

public record ProjectLimit(int maxCount) {

    // 사용자 플랜에 해당하는 프로젝트 제한 객체 생성
    public static ProjectLimit of(UserPlan plan) {
        return new ProjectLimit(plan.maxProjects());
    }

    // 현재 프로젝트 수가 플랜 제한을 초과했는지 확인
    public boolean isExceeded(int currentCount) {
        return currentCount >= maxCount;
    }
}
