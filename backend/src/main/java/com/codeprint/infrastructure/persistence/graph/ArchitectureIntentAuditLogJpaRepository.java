// 예외 규칙 변경 이력 JPA Repository (Spring Data)
package com.codeprint.infrastructure.persistence.graph;

import com.codeprint.domain.graph.ArchitectureIntentAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ArchitectureIntentAuditLogJpaRepository extends JpaRepository<ArchitectureIntentAuditLog, UUID> {

    List<ArchitectureIntentAuditLog> findByProjectIdOrderByCreatedAtDesc(UUID projectId);
}
