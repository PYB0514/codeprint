// 팀 도메인에서 필요한 유저 플랜 정보 포트 인터페이스 (Port & Adapter)
package com.codeprint.domain.team.port;

import java.util.UUID;

public interface UserPlanPort {
    // 유저가 유료(Desktop 라이센스) 플랜인지 확인
    boolean isPaidPlan(UUID userId);
}
