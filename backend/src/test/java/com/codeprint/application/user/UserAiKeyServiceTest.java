// UserAiKeyService 단위 테스트 — 신규 등록/회전 분기, 삭제, 상태조회 회귀 방지
package com.codeprint.application.user;

import com.codeprint.domain.user.AiProvider;
import com.codeprint.domain.user.UserAiKey;
import com.codeprint.domain.user.UserAiKeyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserAiKeyServiceTest {

    @Mock private UserAiKeyRepository userAiKeyRepository;

    private UserAiKeyService service;

    @BeforeEach
    void setUp() {
        service = new UserAiKeyService(userAiKeyRepository);
    }

    // 기존 키가 없으면 신규 생성해서 저장한다
    @Test
    @DisplayName("registerOrRotate — 기존 키 없으면 신규 생성")
    void registerOrRotate_noExisting_createsNew() {
        UUID userId = UUID.randomUUID();
        when(userAiKeyRepository.findByUserIdAndProvider(userId, AiProvider.ANTHROPIC)).thenReturn(Optional.empty());

        service.registerOrRotate(userId, AiProvider.ANTHROPIC, "sk-ant-plain-key");

        ArgumentCaptor<UserAiKey> captor = ArgumentCaptor.forClass(UserAiKey.class);
        verify(userAiKeyRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        assertThat(captor.getValue().getProvider()).isEqualTo(AiProvider.ANTHROPIC);
        assertThat(captor.getValue().getApiKey()).isEqualTo("sk-ant-plain-key");
    }

    // 기존 키가 있으면 새로 만들지 않고 회전(rotate)한다
    @Test
    @DisplayName("registerOrRotate — 기존 키 있으면 회전")
    void registerOrRotate_existing_rotatesInPlace() {
        UUID userId = UUID.randomUUID();
        UserAiKey existing = UserAiKey.create(userId, AiProvider.ANTHROPIC, "old-key");
        when(userAiKeyRepository.findByUserIdAndProvider(userId, AiProvider.ANTHROPIC)).thenReturn(Optional.of(existing));

        service.registerOrRotate(userId, AiProvider.ANTHROPIC, "new-key");

        verify(userAiKeyRepository).save(existing);
        assertThat(existing.getApiKey()).isEqualTo("new-key");
    }

    // 빈 키 등록은 거부한다
    @Test
    @DisplayName("registerOrRotate — 빈 키는 예외")
    void registerOrRotate_blankKey_throws() {
        assertThatThrownBy(() -> service.registerOrRotate(UUID.randomUUID(), AiProvider.ANTHROPIC, "  "))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(userAiKeyRepository);
    }

    // 등록된 키가 하나라도 있으면 true를 반환한다(평문 노출 없음)
    @Test
    @DisplayName("hasAnyKey — 존재 여부만 반환")
    void hasAnyKey_delegatesToRepository() {
        UUID userId = UUID.randomUUID();
        when(userAiKeyRepository.existsByUserId(userId)).thenReturn(true);

        assertThat(service.hasAnyKey(userId)).isTrue();
    }

    // 삭제는 리포지토리에 위임한다
    @Test
    @DisplayName("delete — 리포지토리 삭제 호출")
    void delete_delegatesToRepository() {
        UUID userId = UUID.randomUUID();

        service.delete(userId, AiProvider.OPENAI);

        verify(userAiKeyRepository).deleteByUserIdAndProvider(userId, AiProvider.OPENAI);
    }
}
