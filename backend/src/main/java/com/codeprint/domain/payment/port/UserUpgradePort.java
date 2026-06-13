// Payment 도메인에서 결제 완료 후 user 컨텍스트의 Pro 승급을 요청하는 포트
package com.codeprint.domain.payment.port;

import java.util.UUID;

public interface UserUpgradePort {

    // 사용자를 Pro 플랜으로 승급 (대상 부재 시 no-op)
    void upgradeToPro(UUID userId);
}
