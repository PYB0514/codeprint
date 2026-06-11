// 쪽지 컨텍스트가 User 도메인을 직접 참조하지 않도록 격리하는 포트
package com.codeprint.application.message;

import java.util.Optional;
import java.util.UUID;

public interface UserQueryPort {
    Optional<UserSummaryDto> findById(UUID userId);
}
