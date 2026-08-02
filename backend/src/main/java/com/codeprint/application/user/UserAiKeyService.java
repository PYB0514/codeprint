// 사용자 BYOK(LLM API 키) 등록/조회/삭제 애플리케이션 서비스
package com.codeprint.application.user;

import com.codeprint.shared.ai.AiProvider;
import com.codeprint.domain.user.UserAiKey;
import com.codeprint.domain.user.UserAiKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class UserAiKeyService {

    private final UserAiKeyRepository userAiKeyRepository;

    // 등록된 제공자 목록 — 프론트에서 LLM 기능 호출 시 어느 제공자를 쓸지 선택하는 데 사용(평문 키는 미포함)
    @Transactional(readOnly = true)
    public List<AiProvider> getRegisteredProviders(UUID userId) {
        return userAiKeyRepository.findProvidersByUserId(userId);
    }

    // 신규 등록 또는 기존 키 회전 — 신규 등록 시 priority는 현재 최대값+1(맨 뒤)
    public void registerOrRotate(UUID userId, AiProvider provider, String plainApiKey) {
        if (plainApiKey == null || plainApiKey.isBlank()) {
            throw new IllegalArgumentException("API 키가 비어 있습니다.");
        }
        UserAiKey existing = userAiKeyRepository.findByUserIdAndProvider(userId, provider).orElse(null);
        if (existing != null) {
            existing.rotate(plainApiKey);
            userAiKeyRepository.save(existing);
        } else {
            int nextPriority = userAiKeyRepository.findMaxPriority(userId) + 1;
            userAiKeyRepository.save(UserAiKey.create(userId, provider, plainApiKey, nextPriority));
        }
    }

    // failover 우선순위 재배열 — 등록된 프로바이더 집합과 정확히 일치해야 함(누락·추가 시 거부)
    public void reorderProviders(UUID userId, List<AiProvider> orderedProviders) {
        List<UserAiKey> existing = userAiKeyRepository.findAllByUserId(userId);
        Set<AiProvider> registered = existing.stream().map(UserAiKey::getProvider).collect(Collectors.toSet());
        if (!registered.equals(Set.copyOf(orderedProviders)) || registered.size() != orderedProviders.size()) {
            throw new IllegalArgumentException("등록된 프로바이더 목록과 정확히 일치해야 합니다.");
        }
        Map<AiProvider, UserAiKey> byProvider = existing.stream()
                .collect(Collectors.toMap(UserAiKey::getProvider, k -> k));
        for (int i = 0; i < orderedProviders.size(); i++) {
            UserAiKey key = byProvider.get(orderedProviders.get(i));
            key.changePriority(i);
            userAiKeyRepository.save(key);
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
