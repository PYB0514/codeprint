// UserQueryService 단위 테스트 — 사용자 미존재 시 username 폴백·토큰 Optional 회귀 방지
package com.codeprint.application.user;

import com.codeprint.domain.user.User;
import com.codeprint.domain.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserQueryServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserQueryService service;

    private final UUID userId = UUID.randomUUID();

    @Test
    @DisplayName("username 조회 — 사용자 있으면 username 반환")
    void findUsernameById_found_returnsUsername() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(User.create(1L, "e@x.com", "kim")));

        assertThat(service.findUsernameById(userId)).isEqualTo("kim");
    }

    @Test
    @DisplayName("username 조회 — 사용자 없으면 '알 수 없음' 폴백")
    void findUsernameById_absent_returnsFallback() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThat(service.findUsernameById(userId)).isEqualTo("알 수 없음");
    }

    @Test
    @DisplayName("GitHub 토큰 조회 — 사용자 없으면 빈 Optional")
    void findGithubAccessToken_absent_empty() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThat(service.findGithubAccessToken(userId)).isEmpty();
    }

    @Test
    @DisplayName("GitHub 토큰 조회 — 토큰 저장돼 있으면 해당 값")
    void findGithubAccessToken_present_returnsToken() {
        User user = User.create(1L, "e@x.com", "kim");
        user.updateGithubAccessToken("gh-token");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThat(service.findGithubAccessToken(userId)).contains("gh-token");
    }
}
