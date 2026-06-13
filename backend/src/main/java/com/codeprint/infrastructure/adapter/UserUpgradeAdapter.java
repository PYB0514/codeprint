// Payment UserUpgradePort의 user 컨텍스트 어댑터 — UserRepository로 Pro 승급 수행
package com.codeprint.infrastructure.adapter;

import com.codeprint.domain.payment.port.UserUpgradePort;
import com.codeprint.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserUpgradeAdapter implements UserUpgradePort {

    private final UserRepository userRepository;

    // 사용자를 조회해 Pro로 승급 후 저장 (대상 부재 시 no-op)
    @Override
    public void upgradeToPro(UUID userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.upgradeToPro();
            userRepository.save(user);
            log.info("Pro 업그레이드 완료: userId={}", userId);
        });
    }
}
