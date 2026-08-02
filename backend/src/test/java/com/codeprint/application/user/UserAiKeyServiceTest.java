// UserAiKeyService 단위 테스트 — 신규 등록/회전 분기, 삭제, 상태조회 회귀 방지
package com.codeprint.application.user;

import com.codeprint.shared.ai.AiProvider;
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
        when(userAiKeyRepository.findMaxPriority(userId)).thenReturn(1);

        service.registerOrRotate(userId, AiProvider.ANTHROPIC, "sk-ant-plain-key");

        ArgumentCaptor<UserAiKey> captor = ArgumentCaptor.forClass(UserAiKey.class);
        verify(userAiKeyRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        assertThat(captor.getValue().getProvider()).isEqualTo(AiProvider.ANTHROPIC);
        assertThat(captor.getValue().getApiKey()).isEqualTo("sk-ant-plain-key");
        assertThat(captor.getValue().getPriority()).isEqualTo(2);
    }

    // 기존 키가 있으면 새로 만들지 않고 회전(rotate)한다
    @Test
    @DisplayName("registerOrRotate — 기존 키 있으면 회전")
    void registerOrRotate_existing_rotatesInPlace() {
        UUID userId = UUID.randomUUID();
        UserAiKey existing = UserAiKey.create(userId, AiProvider.ANTHROPIC, "old-key", 0);
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

    @Test
    @DisplayName("getRegisteredProviders — 등록된 제공자 목록을 그대로 반환")
    void getRegisteredProviders_delegatesToRepository() {
        UUID userId = UUID.randomUUID();
        when(userAiKeyRepository.findProvidersByUserId(userId)).thenReturn(java.util.List.of(AiProvider.ANTHROPIC));

        assertThat(service.getRegisteredProviders(userId)).containsExactly(AiProvider.ANTHROPIC);
    }

    // 삭제는 리포지토리에 위임한다
    @Test
    @DisplayName("delete — 리포지토리 삭제 호출")
    void delete_delegatesToRepository() {
        UUID userId = UUID.randomUUID();

        service.delete(userId, AiProvider.OPENAI);

        verify(userAiKeyRepository).deleteByUserIdAndProvider(userId, AiProvider.OPENAI);
    }

    // 등록된 프로바이더 집합과 정확히 일치하는 순서로 priority를 재기록한다
    @Test
    @DisplayName("reorderProviders — 지정한 순서대로 priority 갱신")
    void reorderProviders_validOrder_updatesPriorities() {
        UUID userId = UUID.randomUUID();
        UserAiKey anthropic = UserAiKey.create(userId, AiProvider.ANTHROPIC, "a-key", 0);
        UserAiKey gemini = UserAiKey.create(userId, AiProvider.GEMINI, "g-key", 1);
        when(userAiKeyRepository.findAllByUserId(userId)).thenReturn(java.util.List.of(anthropic, gemini));

        service.reorderProviders(userId, java.util.List.of(AiProvider.GEMINI, AiProvider.ANTHROPIC));

        assertThat(gemini.getPriority()).isEqualTo(0);
        assertThat(anthropic.getPriority()).isEqualTo(1);
        verify(userAiKeyRepository).save(gemini);
        verify(userAiKeyRepository).save(anthropic);
    }

    // 등록되지 않은 프로바이더가 섞이면 거부한다
    @Test
    @DisplayName("reorderProviders — 등록 안 된 프로바이더 포함 시 예외")
    void reorderProviders_unregisteredProvider_throws() {
        UUID userId = UUID.randomUUID();
        UserAiKey anthropic = UserAiKey.create(userId, AiProvider.ANTHROPIC, "a-key", 0);
        when(userAiKeyRepository.findAllByUserId(userId)).thenReturn(java.util.List.of(anthropic));

        assertThatThrownBy(() -> service.reorderProviders(userId, java.util.List.of(AiProvider.ANTHROPIC, AiProvider.OPENAI)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(userAiKeyRepository, never()).save(any());
    }

    // 등록된 프로바이더 일부가 누락되면 거부한다
    @Test
    @DisplayName("reorderProviders — 등록된 프로바이더 누락 시 예외")
    void reorderProviders_missingProvider_throws() {
        UUID userId = UUID.randomUUID();
        UserAiKey anthropic = UserAiKey.create(userId, AiProvider.ANTHROPIC, "a-key", 0);
        UserAiKey gemini = UserAiKey.create(userId, AiProvider.GEMINI, "g-key", 1);
        when(userAiKeyRepository.findAllByUserId(userId)).thenReturn(java.util.List.of(anthropic, gemini));

        assertThatThrownBy(() -> service.reorderProviders(userId, java.util.List.of(AiProvider.ANTHROPIC)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(userAiKeyRepository, never()).save(any());
    }
}
