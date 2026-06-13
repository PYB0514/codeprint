// MessageApplicationService 단위 테스트 — 쪽지 전송 검증·읽음 처리 수신자 권한(IDOR 방지) 회귀 방지
package com.codeprint.application.message;

import com.codeprint.domain.message.DirectMessage;
import com.codeprint.domain.message.DirectMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageApplicationServiceTest {

    @Mock private DirectMessageRepository messageRepository;
    @Mock private UserQueryPort userQueryPort;

    private MessageApplicationService service;

    @BeforeEach
    void setUp() {
        service = new MessageApplicationService(messageRepository, userQueryPort);
    }

    private UserSummaryDto someUser(UUID id) {
        return new UserSummaryDto(id, "user", null);
    }

    // --- send ---

    @Test
    @DisplayName("send — 수신자 존재 + 자기 자신이 아니면 쪽지 저장")
    void send_valid_saves() {
        UUID sender = UUID.randomUUID();
        UUID receiver = UUID.randomUUID();
        when(userQueryPort.findById(receiver)).thenReturn(Optional.of(someUser(receiver)));
        when(messageRepository.save(any(DirectMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        DirectMessage dm = service.send(sender, receiver, "hi");

        assertThat(dm.getSenderId()).isEqualTo(sender);
        assertThat(dm.getReceiverId()).isEqualTo(receiver);
        verify(messageRepository).save(any(DirectMessage.class));
    }

    @Test
    @DisplayName("send — 수신자가 존재하지 않으면 404 NOT_FOUND, 저장 안 함")
    void send_receiverNotFound_notFound() {
        UUID receiver = UUID.randomUUID();
        when(userQueryPort.findById(receiver)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.send(UUID.randomUUID(), receiver, "hi"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(messageRepository, never()).save(any());
    }

    @Test
    @DisplayName("send — 자기 자신에게 보내면 400 BAD_REQUEST, 저장 안 함")
    void send_toSelf_badRequest() {
        UUID me = UUID.randomUUID();
        when(userQueryPort.findById(me)).thenReturn(Optional.of(someUser(me)));

        assertThatThrownBy(() -> service.send(me, me, "hi"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(messageRepository, never()).save(any());
    }

    // --- markRead: 수신자 권한 ---

    @Test
    @DisplayName("markRead — 수신자 본인이면 읽음 처리·저장")
    void markRead_receiver_marksRead() {
        UUID receiver = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        DirectMessage dm = DirectMessage.of(UUID.randomUUID(), receiver, "hi");
        when(messageRepository.findById(messageId)).thenReturn(Optional.of(dm));

        service.markRead(messageId, receiver);

        assertThat(dm.getReadAt()).isNotNull();
        verify(messageRepository).save(dm);
    }

    @Test
    @DisplayName("markRead — 수신자가 아니면 403 FORBIDDEN, 저장 안 함")
    void markRead_notReceiver_forbidden() {
        UUID receiver = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        DirectMessage dm = DirectMessage.of(UUID.randomUUID(), receiver, "hi");
        when(messageRepository.findById(messageId)).thenReturn(Optional.of(dm));

        assertThatThrownBy(() -> service.markRead(messageId, UUID.randomUUID()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThat(dm.getReadAt()).isNull();
        verify(messageRepository, never()).save(any());
    }

    @Test
    @DisplayName("markRead — 쪽지가 존재하지 않으면 404 NOT_FOUND")
    void markRead_notFound() {
        UUID messageId = UUID.randomUUID();
        when(messageRepository.findById(messageId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRead(messageId, UUID.randomUUID()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(messageRepository, never()).save(any());
    }

    // --- getUser ---

    @Test
    @DisplayName("getUser — 존재하면 유저 요약 반환")
    void getUser_exists() {
        UUID userId = UUID.randomUUID();
        when(userQueryPort.findById(userId)).thenReturn(Optional.of(someUser(userId)));

        UserSummaryDto dto = service.getUser(userId);

        assertThat(dto.id()).isEqualTo(userId);
    }

    @Test
    @DisplayName("getUser — 존재하지 않으면 404 NOT_FOUND")
    void getUser_notFound() {
        UUID userId = UUID.randomUUID();
        when(userQueryPort.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getUser(userId))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
