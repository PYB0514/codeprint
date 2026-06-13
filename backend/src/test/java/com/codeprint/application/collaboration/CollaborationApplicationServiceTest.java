// CollaborationApplicationService 단위 테스트 — 세션 get-or-create·초대코드 검증·Free 6인 제한 경계 회귀 방지
package com.codeprint.application.collaboration;

import com.codeprint.domain.collaboration.CollaborationSession;
import com.codeprint.domain.collaboration.CollaborationSessionRepository;
import com.codeprint.domain.collaboration.port.UserInfoPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CollaborationApplicationServiceTest {

    @Mock private CollaborationSessionRepository sessionRepository;
    @Mock private UserInfoPort userInfoPort;

    private CollaborationApplicationService service;

    @BeforeEach
    void setUp() {
        service = new CollaborationApplicationService(sessionRepository, userInfoPort);
    }

    private CollaborationSession sessionWithParticipants(int count) {
        CollaborationSession session = CollaborationSession.create(UUID.randomUUID(), UUID.randomUUID(), "CODE1234");
        for (int i = 0; i < count; i++) {
            session.addParticipant(UUID.randomUUID());
        }
        return session;
    }

    // --- createOrGetSession ---

    @Test
    @DisplayName("createOrGetSession — 기존 세션이 있으면 그대로 반환(신규 생성·저장 안 함)")
    void createOrGetSession_existing_returnsIt() {
        UUID graphId = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        CollaborationSession existing = CollaborationSession.create(graphId, owner, "EXIST123");
        when(sessionRepository.findByGraphIdAndOwnerId(graphId, owner)).thenReturn(Optional.of(existing));

        CollaborationSession result = service.createOrGetSession(graphId, owner);

        assertThat(result).isSameAs(existing);
        verify(sessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("createOrGetSession — 기존 세션이 없으면 생성하고 오너를 참가자로 등록")
    void createOrGetSession_new_createsWithOwner() {
        UUID graphId = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        when(sessionRepository.findByGraphIdAndOwnerId(graphId, owner)).thenReturn(Optional.empty());
        when(sessionRepository.save(any(CollaborationSession.class))).thenAnswer(inv -> inv.getArgument(0));

        CollaborationSession result = service.createOrGetSession(graphId, owner);

        assertThat(result.getOwnerId()).isEqualTo(owner);
        assertThat(result.hasParticipant(owner)).isTrue();
        verify(sessionRepository).save(any(CollaborationSession.class));
    }

    // --- joinSession: 초대코드 + Free 6인 제한 경계 ---

    @Test
    @DisplayName("joinSession — 유효 코드 + 여유 있으면 참가자 추가")
    void joinSession_valid_addsParticipant() {
        CollaborationSession session = sessionWithParticipants(2);
        UUID newUser = UUID.randomUUID();
        when(sessionRepository.findByInviteCode("CODE1234")).thenReturn(Optional.of(session));
        when(sessionRepository.save(session)).thenReturn(session);

        service.joinSession("CODE1234", newUser);

        assertThat(session.hasParticipant(newUser)).isTrue();
        verify(sessionRepository).save(session);
    }

    @Test
    @DisplayName("joinSession — 유효하지 않은 초대 코드면 IllegalArgumentException")
    void joinSession_invalidCode_throws() {
        when(sessionRepository.findByInviteCode("BADCODE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.joinSession("BADCODE", UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("유효하지 않은 초대 코드");
        verify(sessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("joinSession — Free 6인이 가득 찬 상태에서 신규 참가는 거부 (경계값)")
    void joinSession_freeLimitReached_rejected() {
        CollaborationSession session = sessionWithParticipants(6); // 6명 = FREE 한도
        when(sessionRepository.findByInviteCode("CODE1234")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.joinSession("CODE1234", UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("최대 6명");
        verify(sessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("joinSession — 5명일 때 6번째 신규 참가는 허용 (경계 바로 아래)")
    void joinSession_justUnderLimit_allowed() {
        CollaborationSession session = sessionWithParticipants(5);
        UUID newUser = UUID.randomUUID();
        when(sessionRepository.findByInviteCode("CODE1234")).thenReturn(Optional.of(session));
        when(sessionRepository.save(session)).thenReturn(session);

        service.joinSession("CODE1234", newUser);

        assertThat(session.hasParticipant(newUser)).isTrue();
        verify(sessionRepository).save(session);
    }

    @Test
    @DisplayName("joinSession — 6명 가득이어도 이미 참가자인 유저의 재참가는 허용 (제한 우회)")
    void joinSession_alreadyParticipantAtLimit_allowed() {
        CollaborationSession session = CollaborationSession.create(UUID.randomUUID(), UUID.randomUUID(), "CODE1234");
        UUID existingUser = UUID.randomUUID();
        session.addParticipant(existingUser);
        for (int i = 0; i < 5; i++) session.addParticipant(UUID.randomUUID()); // 총 6명, existingUser 포함
        when(sessionRepository.findByInviteCode("CODE1234")).thenReturn(Optional.of(session));
        when(sessionRepository.save(session)).thenReturn(session);

        service.joinSession("CODE1234", existingUser); // 이미 참가자 → 제한 우회

        verify(sessionRepository).save(session);
    }

    // --- getSessionInfo ---

    @Test
    @DisplayName("getSessionInfo — 유효 코드면 세션 정보와 참가자 목록 반환")
    void getSessionInfo_valid_returnsInfo() {
        CollaborationSession session = sessionWithParticipants(1);
        when(sessionRepository.findByInviteCode("CODE1234")).thenReturn(Optional.of(session));
        when(userInfoPort.findUsernameById(any())).thenReturn("alice");

        Map<String, Object> info = service.getSessionInfo("CODE1234");

        assertThat(info.get("inviteCode")).isEqualTo("CODE1234");
        assertThat(info.get("graphId")).isEqualTo(session.getGraphId().toString());
        assertThat(info).containsKey("participants");
    }

    @Test
    @DisplayName("getSessionInfo — 유효하지 않은 초대 코드면 IllegalArgumentException")
    void getSessionInfo_invalidCode_throws() {
        when(sessionRepository.findByInviteCode("BADCODE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSessionInfo("BADCODE"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
