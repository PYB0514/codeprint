// UserAiKey JPA 저장소 인터페이스
package com.codeprint.infrastructure.persistence.user;

import com.codeprint.shared.ai.AiProvider;
import com.codeprint.domain.user.UserAiKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserAiKeyJpaRepository extends JpaRepository<UserAiKey, UUID> {

    Optional<UserAiKey> findByUserIdAndProvider(UUID userId, AiProvider provider);

    boolean existsByUserId(UUID userId);

    void deleteByUserIdAndProvider(UUID userId, AiProvider provider);

    List<UserAiKey> findByUserId(UUID userId);
}
