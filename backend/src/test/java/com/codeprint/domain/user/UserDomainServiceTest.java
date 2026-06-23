// UserDomainService 단위 테스트 — getOrCreate 조회/생성 분기 회귀 방지
package com.codeprint.domain.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDomainServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserDomainService service;

    @Test
    @DisplayName("getOrCreate — 기존 사용자가 있으면 그대로 반환하고 저장하지 않음")
    void getOrCreate_returnsExistingWithoutSaving() {
        User existing = User.create(1L, "e@x.com", "u");
        when(userRepository.findByGithubId(1L)).thenReturn(Optional.of(existing));

        User result = service.getOrCreate(1L, "e@x.com", "u");

        assertThat(result).isSameAs(existing);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("getOrCreate — 기존 사용자가 없으면 새로 생성해 저장")
    void getOrCreate_createsAndSavesWhenAbsent() {
        when(userRepository.findByGithubId(2L)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = service.getOrCreate(2L, "new@x.com", "newbie");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getGithubId()).isEqualTo(2L);
        assertThat(saved.getEmail()).isEqualTo("new@x.com");
        assertThat(saved.getUsername()).isEqualTo("newbie");
        assertThat(result).isSameAs(saved);
    }
}
