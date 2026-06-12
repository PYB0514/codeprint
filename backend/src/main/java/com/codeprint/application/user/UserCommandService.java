// 사용자 생성 및 플랜 변경 애플리케이션 서비스
package com.codeprint.application.user;

import com.codeprint.domain.user.User;
import com.codeprint.domain.user.UserDomainService;
import com.codeprint.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class UserCommandService {

    private final UserRepository userRepository;
    private final UserDomainService userDomainService;

    // GitHub ID로 사용자를 조회하거나 없으면 신규 생성하여 반환
    public User getOrCreateUser(Long githubId, String email, String username) {
        return userDomainService.getOrCreate(githubId, email, username);
    }

    // GitHub OAuth access token을 저장 (로그인 시마다 갱신)
    public void saveGithubAccessToken(UUID userId, String accessToken) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        user.updateGithubAccessToken(accessToken);
        userRepository.save(user);
    }

    // 사용자 플랜을 PRO로 업그레이드
    public void upgradeToPro(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        user.upgradeToPro();
        userRepository.save(user);
    }

    // 사용자 플랜을 FREE로 다운그레이드
    public void downgradeToFree(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        user.downgradeToFree();
        userRepository.save(user);
    }

    // 계정 및 연관 데이터 전체 삭제 (CASCADE 처리)
    public void deleteAccount(UUID userId) {
        userRepository.delete(userId);
    }
}
