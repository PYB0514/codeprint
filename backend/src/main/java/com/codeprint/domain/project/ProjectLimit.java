// 플랜별 프로젝트 생성 제한 Value Object
package com.codeprint.domain.project;

public record ProjectLimit(int maxCount) {

    // 최대 프로젝트 수로 제한 객체 생성
    public static ProjectLimit of(int maxProjects) {
        return new ProjectLimit(maxProjects);
    }

    // 현재 프로젝트 수가 플랜 제한을 초과했는지 확인
    public boolean isExceeded(int currentCount) {
        return currentCount >= maxCount;
    }
}
