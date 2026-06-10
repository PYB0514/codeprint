// 사용자 AI 키 JPA Repository
package com.codeprint.infrastructure.persistence.ai;

import com.codeprint.domain.ai.AiProvider;
import com.codeprint.domain.ai.UserAiKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserAiKeyJpaRepository extends JpaRepository<UserAiKey, UUID>, com.codeprint.domain.ai.UserAiKeyRepository {

    // 사용자ID+공급자로 AI키 조회
    Optional<UserAiKey> findByUserIdAndProvider(UUID userId, AiProvider provider);

    // 사용자 ID로 AI키 목록 조회
    List<UserAiKey> findByUserId(UUID userId);

    // 사용자ID+공급자로 AI키 삭제
    void deleteByUserIdAndProvider(UUID userId, AiProvider provider);
}
