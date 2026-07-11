// 예외 규칙 변경 이력 도메인 Repository JPA 구현체
package com.codeprint.infrastructure.persistence.graph;

import com.codeprint.domain.graph.ArchitectureIntentAuditLog;
import com.codeprint.domain.graph.ArchitectureIntentAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ArchitectureIntentAuditLogRepositoryImpl implements ArchitectureIntentAuditLogRepository {

    private final ArchitectureIntentAuditLogJpaRepository jpa;

    @Override
    public ArchitectureIntentAuditLog save(ArchitectureIntentAuditLog log) {
        return jpa.save(log);
    }

    @Override
    public List<ArchitectureIntentAuditLog> findByProjectIdOrderByCreatedAtDesc(UUID projectId) {
        return jpa.findByProjectIdOrderByCreatedAtDesc(projectId);
    }
}
