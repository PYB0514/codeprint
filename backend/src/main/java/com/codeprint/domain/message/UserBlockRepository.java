// 차단 관계 저장소 도메인 인터페이스
package com.codeprint.domain.message;

import java.util.List;
import java.util.UUID;

public interface UserBlockRepository {

    UserBlock save(UserBlock block);

    // 두 사용자 사이에 (blockerId → blockedId) 차단 관계가 있는지
    boolean existsByBlockerAndBlocked(UUID blockerId, UUID blockedId);

    // 내가 차단한 사용자 목록
    List<UserBlock> findByBlockerId(UUID blockerId);

    // 차단 해제
    void deleteByBlockerAndBlocked(UUID blockerId, UUID blockedId);
}
