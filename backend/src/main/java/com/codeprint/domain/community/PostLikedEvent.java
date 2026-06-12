// 게시글 좋아요 도메인 이벤트 — 알림 발송 side effect 분리용
package com.codeprint.domain.community;

import java.util.UUID;

public record PostLikedEvent(UUID postId, UUID postOwnerId, UUID likerId, String likerUsername) {}
