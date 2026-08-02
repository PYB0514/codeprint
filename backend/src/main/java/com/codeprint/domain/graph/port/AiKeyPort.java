// Graph 도메인에서 user 컨텍스트의 BYOK 키를 조회하는 포트 (user 도메인 모델 비노출)
package com.codeprint.domain.graph.port;

import com.codeprint.shared.ai.AiProvider;

import java.util.Optional;
import java.util.UUID;

public interface AiKeyPort {

    // 사용자의 평문 BYOK 키 조회 — 미등록 시 empty
    Optional<String> findPlainKey(UUID userId, AiProvider provider);
}
