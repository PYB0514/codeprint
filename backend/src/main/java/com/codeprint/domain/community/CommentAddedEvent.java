// 댓글 작성 도메인 이벤트 — 알림 발송 side effect 분리용
package com.codeprint.domain.community;

import java.util.UUID;

public record CommentAddedEvent(UUID postId, UUID postOwnerId, UUID commenterId, String commenterUsername) {}
