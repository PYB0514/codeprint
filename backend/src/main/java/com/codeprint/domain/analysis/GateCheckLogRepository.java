// PR 게이트 체크 결과 기록 도메인 Repository 인터페이스
package com.codeprint.domain.analysis;

import java.util.Optional;
import java.util.UUID;

public interface GateCheckLogRepository {

    // 게이트 체크 결과 저장
    GateCheckLog save(GateCheckLog gateCheckLog);

    // 프로젝트의 가장 최근 게이트 체크 결과 조회 (PR 게이트 연결 상태 화면용)
    Optional<GateCheckLog> findLatestByProjectId(UUID projectId);
}
