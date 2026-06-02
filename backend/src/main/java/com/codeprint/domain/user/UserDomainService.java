// 사용자 도메인 서비스 (신규 사용자 생성 또는 조회)
package com.codeprint.domain.user;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserDomainService {

    private final UserRepository userRepository;

    // GitHub ID로 사용자를 조회하고 없으면 신규 생성
    public User getOrCreate(Long githubId, String email, String username) {
        return userRepository.findByGithubId(githubId)
                .orElseGet(() -> userRepository.save(User.create(githubId, email, username)));
    }
}
