// AI 키 도메인 Repository 인터페이스
package com.codeprint.domain.ai;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserAiKeyRepository {

    // 사용자 ID로 전체 키 조회
    List<UserAiKey> findByUserId(UUID userId);

    // 사용자·제공자로 단일 키 조회
    Optional<UserAiKey> findByUserIdAndProvider(UUID userId, AiProvider provider);

    // AI 키 저장
    UserAiKey save(UserAiKey key);

    // 사용자·제공자 키 삭제
    void deleteByUserIdAndProvider(UUID userId, AiProvider provider);
}
