// NotificationService 단위 테스트 — markRead 소유자 전용 분기(비소유자·미존재 no-op) 회귀 방지
package com.codeprint.application.notification;

import com.codeprint.domain.notification.Notification;
import com.codeprint.domain.notification.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository repository;

    @InjectMocks
    private NotificationService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID otherUserId = UUID.randomUUID();

    @Test
    @DisplayName("알림 생성 — 입력값으로 생성해 저장")
    void create_savesNotification() {
        service.create(userId, "FOLLOW", "님이 팔로우했습니다", "/users/1");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getType()).isEqualTo("FOLLOW");
        assertThat(saved.getMessage()).isEqualTo("님이 팔로우했습니다");
        assertThat(saved.getLink()).isEqualTo("/users/1");
        assertThat(saved.isRead()).isFalse();
    }

    @Test
    @DisplayName("최근 알림 조회 — 레포지토리 위임")
    void getRecent_delegates() {
        Notification n = Notification.create(userId, "DM", "m", "/m");
        when(repository.findTop30ByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(n));

        assertThat(service.getRecent(userId)).containsExactly(n);
    }

    @Test
    @DisplayName("미읽 알림 수 — 레포지토리 위임")
    void getUnreadCount_delegates() {
        when(repository.countByUserIdAndReadFalse(userId)).thenReturn(7L);

        assertThat(service.getUnreadCount(userId)).isEqualTo(7L);
    }

    @Test
    @DisplayName("읽음 처리 — 본인 알림이면 read=true")
    void markRead_owner_marksRead() {
        Notification n = Notification.create(userId, "DM", "m", "/m");
        when(repository.findById(n.getId())).thenReturn(Optional.of(n));

        service.markRead(n.getId(), userId);

        assertThat(n.isRead()).isTrue();
    }

    @Test
    @DisplayName("읽음 처리 — 타인 알림이면 변경 없음(예외 없이 no-op)")
    void markRead_notOwner_noOp() {
        Notification n = Notification.create(userId, "DM", "m", "/m");
        when(repository.findById(n.getId())).thenReturn(Optional.of(n));

        service.markRead(n.getId(), otherUserId);

        assertThat(n.isRead()).isFalse();
    }

    @Test
    @DisplayName("읽음 처리 — 알림 없으면 예외 없이 no-op")
    void markRead_notFound_noOp() {
        UUID missing = UUID.randomUUID();
        when(repository.findById(missing)).thenReturn(Optional.empty());

        service.markRead(missing, userId); // 예외가 나지 않아야 함
    }

    @Test
    @DisplayName("전체 읽음 처리 — 레포지토리 위임")
    void markAllRead_delegates() {
        service.markAllRead(userId);
        verify(repository).markAllReadByUserId(userId);
    }
}
