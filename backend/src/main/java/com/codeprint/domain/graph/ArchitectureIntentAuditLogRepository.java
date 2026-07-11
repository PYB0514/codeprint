// 예외 규칙 변경 이력 도메인 Repository 인터페이스
package com.codeprint.domain.graph;

import java.util.List;
import java.util.UUID;

public interface ArchitectureIntentAuditLogRepository {

    // 변경 이력 저장
    ArchitectureIntentAuditLog save(ArchitectureIntentAuditLog log);

    // 프로젝트의 변경 이력 최신순 조회
    List<ArchitectureIntentAuditLog> findByProjectIdOrderByCreatedAtDesc(UUID projectId);
}
