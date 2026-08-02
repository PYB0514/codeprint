// UserAiKeyRepository 구현체
package com.codeprint.infrastructure.persistence.user;

import com.codeprint.domain.user.AiProvider;
import com.codeprint.domain.user.UserAiKey;
import com.codeprint.domain.user.UserAiKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class UserAiKeyRepositoryImpl implements UserAiKeyRepository {

    private final UserAiKeyJpaRepository jpa;

    @Override
    public Optional<UserAiKey> findByUserIdAndProvider(UUID userId, AiProvider provider) {
        return jpa.findByUserIdAndProvider(userId, provider);
    }

    @Override
    public boolean existsByUserId(UUID userId) {
        return jpa.existsByUserId(userId);
    }

    @Override
    public UserAiKey save(UserAiKey userAiKey) {
        return jpa.save(userAiKey);
    }

    @Override
    @Transactional
    public void deleteByUserIdAndProvider(UUID userId, AiProvider provider) {
        jpa.deleteByUserIdAndProvider(userId, provider);
    }
}
