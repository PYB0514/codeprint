// 게이트 체크 결과 JPA Repository (Spring Data)
package com.codeprint.infrastructure.persistence.analysis;

import com.codeprint.domain.analysis.GateCheckLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GateCheckLogJpaRepository extends JpaRepository<GateCheckLog, UUID> {

    // 프로젝트의 가장 최근 게이트 체크 결과 1건
    Optional<GateCheckLog> findTopByProjectIdOrderByCreatedAtDesc(UUID projectId);
}
