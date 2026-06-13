// 댓글 작성 통합 이벤트 (published language) — 컨텍스트 간 알림 발송용
package com.codeprint.shared.event;

import java.util.UUID;

public record CommentAddedEvent(UUID postId, UUID postOwnerId, UUID commenterId, String commenterUsername) {}
