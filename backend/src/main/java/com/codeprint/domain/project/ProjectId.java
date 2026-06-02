// 프로젝트 ID Value Object
package com.codeprint.domain.project;

import java.util.UUID;

public record ProjectId(UUID value) {

    public static ProjectId of(UUID value) {
        return new ProjectId(value);
    }

    public static ProjectId newId() {
        return new ProjectId(UUID.randomUUID());
    }
}
