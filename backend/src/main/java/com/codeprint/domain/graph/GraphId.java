// 그래프 ID Value Object
package com.codeprint.domain.graph;

import java.util.UUID;

public record GraphId(UUID value) {

    public static GraphId of(UUID value) {
        return new GraphId(value);
    }

    public static GraphId newId() {
        return new GraphId(UUID.randomUUID());
    }
}
