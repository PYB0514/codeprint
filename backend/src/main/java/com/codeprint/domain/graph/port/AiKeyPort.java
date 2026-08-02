// Graph 도메인에서 user 컨텍스트의 BYOK 키를 조회하는 포트 (user 도메인 모델 비노출)
package com.codeprint.domain.graph.port;

import com.codeprint.shared.ai.AiProvider;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiKeyPort {

    // 사용자의 평문 BYOK 키 조회 — 미등록 시 empty
    Optional<String> findPlainKey(UUID userId, AiProvider provider);

    // 사용자가 등록한 프로바이더 목록 — 등록 시각 오름차순(failover 순서 결정에 사용)
    List<AiProvider> findRegisteredProviders(UUID userId);
}
