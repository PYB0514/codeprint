package com.codeprint.domain.community;

import java.util.UUID;

public record PostId(UUID value) {

    public static PostId of(UUID value) {
        return new PostId(value);
    }

    public static PostId newId() {
        return new PostId(UUID.randomUUID());
    }
}
