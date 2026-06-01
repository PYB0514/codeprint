// 노드 ID Value Object
package com.codeprint.domain.graph;

import java.util.UUID;

public record NodeId(UUID value) {

    public static NodeId of(UUID value) {
        return new NodeId(value);
    }

    public static NodeId newId() {
        return new NodeId(UUID.randomUUID());
    }
}
