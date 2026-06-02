// 그래프 ID Value Object
package com.codeprint.domain.graph;

import java.util.UUID;

public record GraphId(UUID value) {

    // UUID로 GraphId 생성
    public static GraphId of(UUID value) {
        return new GraphId(value);
    }

    // 새 랜덤 GraphId 생성
    public static GraphId newId() {
        return new GraphId(UUID.randomUUID());
    }
}
