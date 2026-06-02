// 게시글 ID Value Object
package com.codeprint.domain.community;

import java.util.UUID;

public record PostId(UUID value) {

    // UUID로 PostId 생성
    public static PostId of(UUID value) {
        return new PostId(value);
    }

    // 새 랜덤 PostId 생성
    public static PostId newId() {
        return new PostId(UUID.randomUUID());
    }
}
