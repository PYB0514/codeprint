// 오탐 신고 Spring Data JPA 리포지토리
package com.codeprint.infrastructure.persistence.graph;

import com.codeprint.domain.graph.FpReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FpReportJpaRepository extends JpaRepository<FpReport, UUID> {

    List<FpReport> findByProjectIdAndReporterId(UUID projectId, UUID reporterId);

    boolean existsByProjectIdAndFingerprintAndReporterId(UUID projectId, String fingerprint, UUID reporterId);
}
