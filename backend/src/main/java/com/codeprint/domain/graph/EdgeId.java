// 엣지 ID Value Object
package com.codeprint.domain.graph;

import java.util.UUID;

public record EdgeId(UUID value) {

    // UUID로 EdgeId 생성
    public static EdgeId of(UUID value) {
        return new EdgeId(value);
    }

    // 새 랜덤 EdgeId 생성
    public static EdgeId newId() {
        return new EdgeId(UUID.randomUUID());
    }
}
