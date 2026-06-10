// 분석 결과 JPA Repository (Spring Data)
package com.codeprint.infrastructure.persistence.analysis;

import com.codeprint.domain.analysis.AnalysisResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnalysisJpaRepository extends JpaRepository<AnalysisResult, UUID> {

    // 프로젝트 ID로 분석 결과 목록 조회
    List<AnalysisResult> findByProjectId(UUID projectId);

    // 프로젝트의 최신 분석 결과 조회
    @Query("SELECT a FROM AnalysisResult a WHERE a.projectId = :projectId ORDER BY a.createdAt DESC LIMIT 1")
    Optional<AnalysisResult> findLatestByProjectId(UUID projectId);

    // 브랜치별 최신 분석 결과 조회
    @Query("SELECT a FROM AnalysisResult a WHERE a.projectId = :projectId AND a.branch = :branch ORDER BY a.createdAt DESC LIMIT 1")
    Optional<AnalysisResult> findLatestByProjectIdAndBranch(UUID projectId, String branch);
}
