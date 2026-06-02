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

    public User getOrCreateUser(Long githubId, String email, String username) {
        return userDomainService.getOrCreate(githubId, email, username);
    }

    public void upgradeToPro(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        user.upgradeToPro();
        userRepository.save(user);
    }

    public void downgradeToFree(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        user.downgradeToFree();
        userRepository.save(user);
    }
}
