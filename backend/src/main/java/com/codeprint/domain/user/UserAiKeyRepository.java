// BYOK 키 저장소 인터페이스
package com.codeprint.domain.user;

import java.util.Optional;
import java.util.UUID;

public interface UserAiKeyRepository {

    // 사용자+제공자 조합으로 조회
    Optional<UserAiKey> findByUserIdAndProvider(UUID userId, AiProvider provider);

    // 사용자의 키 등록 여부(제공자 무관)
    boolean existsByUserId(UUID userId);

    // 저장(신규 등록/회전 공용)
    UserAiKey save(UserAiKey userAiKey);

    // 삭제
    void deleteByUserIdAndProvider(UUID userId, AiProvider provider);
}
