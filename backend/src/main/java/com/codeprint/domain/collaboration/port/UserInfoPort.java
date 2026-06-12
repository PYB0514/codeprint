// 협업 도메인에서 필요한 유저 정보 포트 인터페이스 (Port & Adapter)
package com.codeprint.domain.collaboration.port;

import java.util.UUID;

public interface UserInfoPort {
    // 유저 ID로 사용자명 조회
    String findUsernameById(UUID userId);
}
