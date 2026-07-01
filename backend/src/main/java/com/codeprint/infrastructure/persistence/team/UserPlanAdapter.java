// UserPlanPort 구현체 — UserRepository를 통해 유저 플랜 정보를 팀 도메인에 제공
package com.codeprint.infrastructure.persistence.team;

import com.codeprint.domain.team.port.UserPlanPort;
import com.codeprint.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UserPlanAdapter implements UserPlanPort {

    private final UserRepository userRepository;

    // 유저가 유료(Desktop 라이센스) 플랜인지 확인
    @Override
    public boolean isPaidPlan(UUID userId) {
        return userRepository.findById(userId)
                .map(u -> u.getPlan().isPaid())
                .orElse(false);
    }
}
