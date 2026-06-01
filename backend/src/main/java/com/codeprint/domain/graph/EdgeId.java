package com.codeprint.domain.graph;

import java.util.UUID;

public record EdgeId(UUID value) {

    public static EdgeId of(UUID value) {
        return new EdgeId(value);
    }

    public static EdgeId newId() {
        return new EdgeId(UUID.randomUUID());
    }
}
