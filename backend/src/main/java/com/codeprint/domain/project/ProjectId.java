// 프로젝트 ID Value Object
package com.codeprint.domain.project;

import java.util.UUID;

public record ProjectId(UUID value) {

    // UUID로 ProjectId 생성
    public static ProjectId of(UUID value) {
        return new ProjectId(value);
    }

    // 새 랜덤 ProjectId 생성
    public static ProjectId newId() {
        return new ProjectId(UUID.randomUUID());
    }
}
