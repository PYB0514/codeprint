// 차단 관계 저장소 구현체
package com.codeprint.infrastructure.persistence.message;

import com.codeprint.domain.message.UserBlock;
import com.codeprint.domain.message.UserBlockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class UserBlockRepositoryImpl implements UserBlockRepository {

    private final UserBlockJpaBaseRepository jpa;

    @Override
    public UserBlock save(UserBlock block) {
        return jpa.save(block);
    }

    @Override
    public boolean existsByBlockerAndBlocked(UUID blockerId, UUID blockedId) {
        return jpa.existsByBlockerIdAndBlockedId(blockerId, blockedId);
    }

    @Override
    public List<UserBlock> findByBlockerId(UUID blockerId) {
        return jpa.findByBlockerId(blockerId);
    }

    @Override
    public void deleteByBlockerAndBlocked(UUID blockerId, UUID blockedId) {
        jpa.deleteByBlockerIdAndBlockedId(blockerId, blockedId);
    }
}
