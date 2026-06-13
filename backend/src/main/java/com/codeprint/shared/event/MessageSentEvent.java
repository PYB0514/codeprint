// 쪽지 전송 통합 이벤트 (published language) — 컨텍스트 간 알림 발송용
package com.codeprint.shared.event;

import java.util.UUID;

public record MessageSentEvent(UUID senderId, String senderUsername, UUID receiverId, String content) {}
