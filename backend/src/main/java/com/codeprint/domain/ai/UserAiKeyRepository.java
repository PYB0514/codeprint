// AI 키 도메인 Repository 인터페이스
package com.codeprint.domain.ai;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserAiKeyRepository {

    List<UserAiKey> findByUserId(UUID userId);

    Optional<UserAiKey> findByUserIdAndProvider(UUID userId, AiProvider provider);

    UserAiKey save(UserAiKey key);

    void deleteByUserIdAndProvider(UUID userId, AiProvider provider);
}
