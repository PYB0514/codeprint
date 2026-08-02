// 사용자 BYOK(LLM API 키) 등록/조회/삭제 애플리케이션 서비스
package com.codeprint.application.user;

import com.codeprint.domain.user.AiProvider;
import com.codeprint.domain.user.UserAiKey;
import com.codeprint.domain.user.UserAiKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class UserAiKeyService {

    private final UserAiKeyRepository userAiKeyRepository;

    // 신규 등록 또는 기존 키 회전
    public void registerOrRotate(UUID userId, AiProvider provider, String plainApiKey) {
        if (plainApiKey == null || plainApiKey.isBlank()) {
            throw new IllegalArgumentException("API 키가 비어 있습니다.");
        }
        UserAiKey existing = userAiKeyRepository.findByUserIdAndProvider(userId, provider).orElse(null);
        if (existing != null) {
            existing.rotate(plainApiKey);
            userAiKeyRepository.save(existing);
        } else {
            userAiKeyRepository.save(UserAiKey.create(userId, provider, plainApiKey));
        }
    }

    // 삭제
    public void delete(UUID userId, AiProvider provider) {
        userAiKeyRepository.deleteByUserIdAndProvider(userId, provider);
    }

    // 등록 여부(제공자 무관) — 프론트 노출용, 평문 키는 절대 반환하지 않음
    @Transactional(readOnly = true)
    public boolean hasAnyKey(UUID userId) {
        return userAiKeyRepository.existsByUserId(userId);
    }

    // 내부 소비용(레이어A/B 등) — 평문 키 조회, 컨트롤러 응답에 절대 포함 금지
    @Transactional(readOnly = true)
    public Optional<String> getPlainKey(UUID userId, AiProvider provider) {
        return userAiKeyRepository.findByUserIdAndProvider(userId, provider).map(UserAiKey::getApiKey);
    }
}
