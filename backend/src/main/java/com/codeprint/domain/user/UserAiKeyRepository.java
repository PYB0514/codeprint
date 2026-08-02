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

    // 사용자가 등록한 제공자 목록(우선순위순) — failover 순서·프론트 선택에 사용
    List<AiProvider> findProvidersByUserId(UUID userId);

    // 사용자의 등록 키 전체(우선순위순) — 재배열(reorder) 시 개별 엔티티 갱신에 사용
    List<UserAiKey> findAllByUserId(UUID userId);

    // 사용자의 현재 최대 우선순위 — 신규 등록 시 맨 뒤(max+1)에 배치하는 데 사용, 등록된 키 없으면 -1
    int findMaxPriority(UUID userId);

    // 저장(신규 등록/회전 공용)
    UserAiKey save(UserAiKey userAiKey);

    // 삭제
    void deleteByUserIdAndProvider(UUID userId, AiProvider provider);
}
