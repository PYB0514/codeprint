// GraphUserInfoPort 구현체 — UserRepository를 통해 유저 정보를 graph 도메인에 제공
package com.codeprint.infrastructure.adapter;

import com.codeprint.domain.graph.port.GraphUserInfoPort;
import com.codeprint.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class GraphUserInfoAdapter implements GraphUserInfoPort {

    private final UserRepository userRepository;

    // userId로 username·배경이미지 원본 URL 조회
    @Override
    public Optional<UserInfo> findUserInfo(UUID userId) {
        return userRepository.findById(userId)
                .map(u -> new UserInfo(u.getUsername(), u.getGraphBgUrl()));
    }
}
