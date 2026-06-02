// 노드 ID Value Object
package com.codeprint.domain.graph;

import java.util.UUID;

public record NodeId(UUID value) {

    // UUID로 NodeId 생성
    public static NodeId of(UUID value) {
        return new NodeId(value);
    }

    // 새 랜덤 NodeId 생성
    public static NodeId newId() {
        return new NodeId(UUID.randomUUID());
    }
}
