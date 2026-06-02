// 사용자 ID Value Object
package com.codeprint.domain.user;

import java.util.UUID;

public record UserId(UUID value) {

    // UUID로 UserId 생성
    public static UserId of(UUID value) {
        return new UserId(value);
    }

    // 새 랜덤 UserId 생성
    public static UserId newId() {
        return new UserId(UUID.randomUUID());
    }
}
