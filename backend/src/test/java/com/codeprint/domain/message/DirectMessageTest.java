// DirectMessage 단위 테스트 — 읽음 처리 멱등성 회귀 방지
package com.codeprint.domain.message;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DirectMessageTest {

    private final UUID sender = UUID.randomUUID();
    private final UUID receiver = UUID.randomUUID();

    @Test
    @DisplayName("of — 생성 직후 미읽음(readAt null)")
    void of_isUnread() {
        DirectMessage dm = DirectMessage.of(sender, receiver, "안녕");
        assertThat(dm.getReadAt()).isNull();
        assertThat(dm.getSenderId()).isEqualTo(sender);
        assertThat(dm.getReceiverId()).isEqualTo(receiver);
    }

    @Test
    @DisplayName("markAsRead — 미읽음에서 readAt 설정")
    void markAsRead_setsReadAt() {
        DirectMessage dm = DirectMessage.of(sender, receiver, "안녕");

        dm.markAsRead();

        assertThat(dm.getReadAt()).isNotNull();
    }

    @Test
    @DisplayName("markAsRead — 이미 읽은 쪽지는 readAt 유지 (멱등)")
    void markAsRead_idempotent() {
        DirectMessage dm = DirectMessage.of(sender, receiver, "안녕");
        dm.markAsRead();
        Instant first = dm.getReadAt();

        dm.markAsRead();

        assertThat(dm.getReadAt()).isEqualTo(first);
    }
}
