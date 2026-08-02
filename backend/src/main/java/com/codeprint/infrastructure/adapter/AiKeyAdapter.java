// Graph AiKeyPort의 user 컨텍스트 어댑터 — BYOK 키 조회
package com.codeprint.infrastructure.adapter;

import com.codeprint.application.user.UserAiKeyService;
import com.codeprint.domain.graph.port.AiKeyPort;
import com.codeprint.shared.ai.AiProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AiKeyAdapter implements AiKeyPort {

    private final UserAiKeyService userAiKeyService;

    @Override
    public Optional<String> findPlainKey(UUID userId, AiProvider provider) {
        return userAiKeyService.getPlainKey(userId, provider);
    }
}
