// 사용자 도메인 서비스 (신규 사용자 생성 또는 조회)
package com.codeprint.domain.user;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserDomainService {

    private final UserRepository userRepository;

    public User getOrCreate(Long githubId, String email, String username) {
        return userRepository.findByGithubId(githubId)
                .orElseGet(() -> userRepository.save(User.create(githubId, email, username)));
    }
}
