// 댓글 ID Value Object
package com.codeprint.domain.community;

import java.util.UUID;

public record CommentId(UUID value) {

    // UUID로 CommentId 생성
    public static CommentId of(UUID value) {
        return new CommentId(value);
    }

    // 새 랜덤 CommentId 생성
    public static CommentId newId() {
        return new CommentId(UUID.randomUUID());
    }
}
