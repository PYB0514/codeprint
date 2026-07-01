// UserCommandService 단위 테스트 — 미존재 분기·플랜 전이·토큰 갱신 회귀 방지
package com.codeprint.application.user;

import com.codeprint.domain.user.User;
import com.codeprint.domain.user.UserDomainService;
import com.codeprint.domain.user.UserRepository;
import com.codeprint.shared.plan.UserPlan;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserCommandServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserDomainService userDomainService;

    @InjectMocks
    private UserCommandService service;

    private final UUID userId = UUID.randomUUID();

    @Test
    @DisplayName("getOrCreateUser — 도메인 서비스에 위임")
    void getOrCreateUser_delegates() {
        User user = User.create(1L, "e@x.com", "u");
        when(userDomainService.getOrCreate(1L, "e@x.com", "u")).thenReturn(user);

        assertThat(service.getOrCreateUser(1L, "e@x.com", "u")).isSameAs(user);
    }

    @Test
    @DisplayName("토큰 저장 — 사용자 없으면 예외, 저장 안 함")
    void saveGithubAccessToken_notFound_throws() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.saveGithubAccessToken(userId, "tok"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("토큰 저장 — 토큰 갱신 후 저장")
    void saveGithubAccessToken_updatesAndSaves() {
        User user = User.create(1L, "e@x.com", "u");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        service.saveGithubAccessToken(userId, "new-token");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getGithubAccessToken()).isEqualTo("new-token");
    }

    @Test
    @DisplayName("PRO 업그레이드 — 사용자 없으면 예외")
    void upgradeToPro_notFound_throws() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.upgradeToPro(userId))
                .isInstanceOf(IllegalArgumentException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("PRO 업그레이드 — plan=DESKTOP 저장")
    void upgradeToPro_setsPlan() {
        User user = User.create(1L, "e@x.com", "u"); // 기본 FREE
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        service.upgradeToPro(userId);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPlan()).isEqualTo(UserPlan.DESKTOP);
    }

    @Test
    @DisplayName("FREE 다운그레이드 — plan=FREE 저장")
    void downgradeToFree_setsPlan() {
        User user = User.create(1L, "e@x.com", "u");
        user.upgradeToPro();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        service.downgradeToFree(userId);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPlan()).isEqualTo(UserPlan.FREE);
    }

    @Test
    @DisplayName("계정 삭제 — 레포지토리 delete 위임")
    void deleteAccount_delegates() {
        service.deleteAccount(userId);
        verify(userRepository).delete(userId);
    }
}
