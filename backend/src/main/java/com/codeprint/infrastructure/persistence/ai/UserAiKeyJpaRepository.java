// 사용자 AI 키 JPA Repository
package com.codeprint.infrastructure.persistence.ai;

import com.codeprint.domain.ai.AiProvider;
import com.codeprint.domain.ai.UserAiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserAiKeyJpaRepository extends JpaRepository<UserAiKey, UUID>, com.codeprint.domain.ai.UserAiKeyRepository {

    // 사용자ID+공급자로 AI키 조회
    Optional<UserAiKey> findByUserIdAndProvider(UUID userId, AiProvider provider);

    // 사용자 ID로 AI키 목록 조회
    List<UserAiKey> findByUserId(UUID userId);

    // 사용자ID+공급자로 AI키 삭제 — 파생 delete 쿼리는 트랜잭션 필요(반복-A #154와 동일 원인 클래스)
    @Override
    @Transactional
    void deleteByUserIdAndProvider(UUID userId, AiProvider provider);
}
