// 사용자 읽기 전용 조회 — 타 컨텍스트에서 사용자 정보가 필요할 때 사용
package com.codeprint.application.user;

import com.codeprint.domain.user.User;
import com.codeprint.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserQueryService {

    private final UserRepository userRepository;

    // 사용자 ID로 username 조회 (없으면 "알 수 없음")
    public String findUsernameById(UUID userId) {
        return userRepository.findById(userId)
                .map(u -> u.getUsername())
                .orElse("알 수 없음");
    }

    // 사용자의 GitHub 액세스 토큰 조회 — webhook이 소유자 토큰으로 코멘트를 게시할 때 사용
    public Optional<String> findGithubAccessToken(UUID userId) {
        return userRepository.findById(userId).map(User::getGithubAccessToken);
    }
}
