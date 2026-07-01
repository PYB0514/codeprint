// UserInfoPort 구현체 — UserRepository를 통해 유저 정보를 협업 도메인에 제공
package com.codeprint.infrastructure.persistence.collaboration;

import com.codeprint.domain.collaboration.port.UserInfoPort;
import com.codeprint.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UserInfoAdapter implements UserInfoPort {

    private final UserRepository userRepository;

    // 유저 ID로 사용자명 조회
    @Override
    public String findUsernameById(UUID userId) {
        return userRepository.findById(userId)
                .map(u -> u.getUsername())
                .orElse("(알 수 없음)");
    }

    // 유저가 유료(Desktop 라이센스) 플랜인지 확인
    @Override
    public boolean isPaidPlan(UUID userId) {
        return userRepository.findById(userId)
                .map(u -> u.getPlan().isPaid())
                .orElse(false);
    }
}
