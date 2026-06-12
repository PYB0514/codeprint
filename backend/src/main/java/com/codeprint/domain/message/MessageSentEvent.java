// 쪽지 전송 도메인 이벤트 — 알림 발송 side effect 분리용
package com.codeprint.domain.message;

import java.util.UUID;

public record MessageSentEvent(UUID senderId, String senderUsername, UUID receiverId, String content) {}
