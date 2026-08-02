// UserAiKey JPA 저장소 인터페이스
package com.codeprint.infrastructure.persistence.user;

import com.codeprint.shared.ai.AiProvider;
import com.codeprint.domain.user.UserAiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserAiKeyJpaRepository extends JpaRepository<UserAiKey, UUID> {

    Optional<UserAiKey> findByUserIdAndProvider(UUID userId, AiProvider provider);

    boolean existsByUserId(UUID userId);

    void deleteByUserIdAndProvider(UUID userId, AiProvider provider);

    // failover 순서 결정에 쓰이므로 명시적 정렬 필요(ORDER BY 없으면 비결정적)
    List<UserAiKey> findByUserIdOrderByPriorityAsc(UUID userId);

    // 신규 등록 시 맨 뒤에 배치할 priority 계산용 — 등록된 키 없으면 null(구현체에서 -1로 치환)
    @Query("SELECT MAX(k.priority) FROM UserAiKey k WHERE k.userId = :userId")
    Integer findMaxPriority(UUID userId);
}
