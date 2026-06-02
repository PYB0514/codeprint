// 댓글 ID Value Object
package com.codeprint.domain.community;

import java.util.UUID;

public record CommentId(UUID value) {

    public static CommentId of(UUID value) {
        return new CommentId(value);
    }

    public static CommentId newId() {
        return new CommentId(UUID.randomUUID());
    }
}
