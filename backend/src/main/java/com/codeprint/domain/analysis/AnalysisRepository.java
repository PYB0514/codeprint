// 분석 결과 도메인 Repository 인터페이스
package com.codeprint.domain.analysis;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnalysisRepository {

    AnalysisResult save(AnalysisResult analysisResult);

    Optional<AnalysisResult> findById(UUID id);

    List<AnalysisResult> findByProjectId(UUID projectId);

    Optional<AnalysisResult> findLatestByProjectId(UUID projectId);

    // 특정 브랜치의 가장 최근 완료 분석 조회
    Optional<AnalysisResult> findLatestByProjectIdAndBranch(UUID projectId, String branch);

    // 어드민 전용 — 전체 분석 횟수 조회
    long count();
}
