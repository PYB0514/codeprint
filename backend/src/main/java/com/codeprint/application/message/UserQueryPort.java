// 쪽지 컨텍스트가 User 도메인을 직접 참조하지 않도록 격리하는 포트
package com.codeprint.application.message;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserQueryPort {
    Optional<UserSummaryDto> findById(UUID userId);

    // 여러 유저 요약을 일괄 조회 (쪽지 목록 작성자/수신자 N+1 제거용)
    List<UserSummaryDto> findByIds(List<UUID> userIds);
}
