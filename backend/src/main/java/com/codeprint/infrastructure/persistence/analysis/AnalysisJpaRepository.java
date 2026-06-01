package com.codeprint.infrastructure.persistence.analysis;

import com.codeprint.domain.analysis.AnalysisResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnalysisJpaRepository extends JpaRepository<AnalysisResult, UUID> {

    List<AnalysisResult> findByProjectId(UUID projectId);

    @Query("SELECT a FROM AnalysisResult a WHERE a.projectId = :projectId ORDER BY a.createdAt DESC LIMIT 1")
    Optional<AnalysisResult> findLatestByProjectId(UUID projectId);
}
