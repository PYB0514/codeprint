// Community 도메인 FollowQueryPort의 User 인프라 구현체
package com.codeprint.infrastructure.persistence.user;

import com.codeprint.domain.community.port.FollowQueryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class FollowQueryPortImpl implements FollowQueryPort {

    private final UserFollowJpaRepository userFollowJpaRepository;

    // 팔로우 대상 유저 ID 목록 반환
    @Override
    public List<UUID> findFollowingIds(UUID followerId) {
        return userFollowJpaRepository.findByFollowerId(followerId).stream()
                .map(f -> f.getFollowingId())
                .toList();
    }
}
