// 경고 suppress Spring Data JPA 리포지토리
package com.codeprint.infrastructure.persistence.graph;

import com.codeprint.domain.graph.WarningSuppression;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WarningSuppressionJpaRepository extends JpaRepository<WarningSuppression, UUID> {

    List<WarningSuppression> findByProjectId(UUID projectId);

    boolean existsByProjectIdAndFingerprint(UUID projectId, String fingerprint);

    void deleteByProjectIdAndFingerprint(UUID projectId, String fingerprint);
}
