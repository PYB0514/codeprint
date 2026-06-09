// 사용자 읽기 전용 조회 — 타 컨텍스트에서 사용자 정보가 필요할 때 사용
package com.codeprint.application.user;

import com.codeprint.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
