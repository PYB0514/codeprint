// UserQueryPort 구현체 — message 컨텍스트가 User 도메인을 직접 참조하지 않도록 격리
package com.codeprint.infrastructure.persistence.user;

import com.codeprint.application.message.UserQueryPort;
import com.codeprint.application.message.UserSummaryDto;
import com.codeprint.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UserQueryPortImpl implements UserQueryPort {

    private final UserRepository userRepository;

    // 유저 요약 정보 조회 — 쪽지 컨텍스트 전용
    @Override
    public Optional<UserSummaryDto> findById(UUID userId) {
        return userRepository.findById(userId)
                .map(u -> new UserSummaryDto(u.getId(), u.getUsername(), u.getAvatarUrl()));
    }

    // 여러 유저 요약 일괄 조회 — findByIdIn 배치
    @Override
    public List<UserSummaryDto> findByIds(List<UUID> userIds) {
        return userRepository.findByIdIn(userIds).stream()
                .map(u -> new UserSummaryDto(u.getId(), u.getUsername(), u.getAvatarUrl()))
                .toList();
    }
}
