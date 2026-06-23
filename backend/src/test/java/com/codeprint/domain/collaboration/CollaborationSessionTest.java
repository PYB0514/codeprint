// CollaborationSession 단위 테스트 — 참가자 추가 멱등성·존재 확인 분기 회귀 방지
package com.codeprint.domain.collaboration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CollaborationSessionTest {

    private final UUID graphId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();

    // 생성 헬퍼
    private CollaborationSession newSession() {
        return CollaborationSession.create(graphId, ownerId, "ABCD1234");
    }

    @Test
    @DisplayName("create — 신규 세션은 참가자 없음")
    void create_hasNoParticipants() {
        CollaborationSession s = newSession();
        assertThat(s.getParticipants()).isEmpty();
    }

    @Test
    @DisplayName("hasParticipant — 미참가 사용자는 false")
    void hasParticipant_falseWhenAbsent() {
        CollaborationSession s = newSession();
        assertThat(s.hasParticipant(UUID.randomUUID())).isFalse();
    }

    @Test
    @DisplayName("addParticipant — 신규 사용자 추가 후 hasParticipant true")
    void addParticipant_addsNewUser() {
        CollaborationSession s = newSession();
        UUID userId = UUID.randomUUID();

        s.addParticipant(userId);

        assertThat(s.hasParticipant(userId)).isTrue();
        assertThat(s.getParticipants()).hasSize(1);
    }

    @Test
    @DisplayName("addParticipant — 같은 사용자 중복 추가는 무시 (멱등)")
    void addParticipant_idempotentForSameUser() {
        CollaborationSession s = newSession();
        UUID userId = UUID.randomUUID();

        s.addParticipant(userId);
        s.addParticipant(userId);

        assertThat(s.getParticipants()).hasSize(1);
    }

    @Test
    @DisplayName("addParticipant — 서로 다른 사용자는 각각 추가")
    void addParticipant_addsDistinctUsers() {
        CollaborationSession s = newSession();

        s.addParticipant(UUID.randomUUID());
        s.addParticipant(UUID.randomUUID());

        assertThat(s.getParticipants()).hasSize(2);
    }
}
