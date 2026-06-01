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
}
