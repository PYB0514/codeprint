// BYOK 키 저장소 인터페이스
package com.codeprint.domain.user;

import com.codeprint.shared.ai.AiProvider;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserAiKeyRepository {

    // 사용자+제공자 조합으로 조회
    Optional<UserAiKey> findByUserIdAndProvider(UUID userId, AiProvider provider);

    // 사용자의 키 등록 여부(제공자 무관)
    boolean existsByUserId(UUID userId);

    // 사용자가 등록한 제공자 목록 — 프론트에서 어느 제공자로 LLM 기능을 호출할지 선택하는 데 사용
    List<AiProvider> findProvidersByUserId(UUID userId);

    // 저장(신규 등록/회전 공용)
    UserAiKey save(UserAiKey userAiKey);

    // 삭제
    void deleteByUserIdAndProvider(UUID userId, AiProvider provider);
}
