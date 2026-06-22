// NotificationSettingsApplicationService 단위 테스트 — 설정 없을 때 기본값 분기 회귀 방지
package com.codeprint.application.notification;

import com.codeprint.domain.notification.NotificationSettingsRepository;
import com.codeprint.domain.notification.UserNotificationSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationSettingsApplicationServiceTest {

    @Mock
    private NotificationSettingsRepository repository;

    @InjectMocks
    private NotificationSettingsApplicationService service;

    private final UUID userId = UUID.randomUUID();

    @Test
    @DisplayName("조회 — 저장된 설정이 있으면 그대로 반환")
    void get_stored_returnsStored() {
        UserNotificationSettings stored = UserNotificationSettings.defaultFor(userId);
        stored.update(false, false);
        when(repository.findById(userId)).thenReturn(Optional.of(stored));

        assertThat(service.get(userId)).isSameAs(stored);
    }

    @Test
    @DisplayName("조회 — 설정 없으면 기본값(teamChat·dm 모두 true)")
    void get_absent_returnsDefault() {
        when(repository.findById(userId)).thenReturn(Optional.empty());

        UserNotificationSettings result = service.get(userId);

        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.isTeamChat()).isTrue();
        assertThat(result.isDm()).isTrue();
    }

    @Test
    @DisplayName("DM 푸시 여부 — 저장값 dm=false면 false")
    void isDmPushEnabled_storedFalse() {
        UserNotificationSettings stored = UserNotificationSettings.defaultFor(userId);
        stored.update(true, false);
        when(repository.findById(userId)).thenReturn(Optional.of(stored));

        assertThat(service.isDmPushEnabled(userId)).isFalse();
    }

    @Test
    @DisplayName("DM 푸시 여부 — 설정 없으면 기본 true")
    void isDmPushEnabled_absentDefaultsTrue() {
        when(repository.findById(userId)).thenReturn(Optional.empty());

        assertThat(service.isDmPushEnabled(userId)).isTrue();
    }

    @Test
    @DisplayName("수정 — 설정 없으면 기본값 생성 후 갱신 저장")
    void update_absent_createsThenSaves() {
        when(repository.findById(userId)).thenReturn(Optional.empty());
        when(repository.save(any(UserNotificationSettings.class))).thenAnswer(inv -> inv.getArgument(0));

        service.update(userId, false, true);

        ArgumentCaptor<UserNotificationSettings> captor =
                ArgumentCaptor.forClass(UserNotificationSettings.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        assertThat(captor.getValue().isTeamChat()).isFalse();
        assertThat(captor.getValue().isDm()).isTrue();
    }

    @Test
    @DisplayName("수정 — 기존 설정이 있으면 같은 엔티티 갱신 저장")
    void update_existing_updatesSameEntity() {
        UserNotificationSettings existing = UserNotificationSettings.defaultFor(userId);
        when(repository.findById(userId)).thenReturn(Optional.of(existing));
        when(repository.save(any(UserNotificationSettings.class))).thenAnswer(inv -> inv.getArgument(0));

        service.update(userId, false, false);

        ArgumentCaptor<UserNotificationSettings> captor =
                ArgumentCaptor.forClass(UserNotificationSettings.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue()).isSameAs(existing);
        assertThat(captor.getValue().isTeamChat()).isFalse();
        assertThat(captor.getValue().isDm()).isFalse();
    }
}
