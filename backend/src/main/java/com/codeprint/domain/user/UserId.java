// 사용자 ID Value Object
package com.codeprint.domain.user;

import java.util.UUID;

public record UserId(UUID value) {

    public static UserId of(UUID value) {
        return new UserId(value);
    }

    public static UserId newId() {
        return new UserId(UUID.randomUUID());
    }
}
