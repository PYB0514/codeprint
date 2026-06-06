// 사용자 AI 키 JPA Repository
package com.codeprint.infrastructure.persistence.ai;

import com.codeprint.domain.ai.AiProvider;
import com.codeprint.domain.ai.UserAiKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserAiKeyJpaRepository extends JpaRepository<UserAiKey, UUID> {

    Optional<UserAiKey> findByUserIdAndProvider(UUID userId, AiProvider provider);

    List<UserAiKey> findByUserId(UUID userId);

    void deleteByUserIdAndProvider(UUID userId, AiProvider provider);
}
