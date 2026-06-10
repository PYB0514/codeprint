// 분석 결과 도메인 Repository 인터페이스
package com.codeprint.domain.analysis;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnalysisRepository {

    // 분석 결과 저장
    AnalysisResult save(AnalysisResult analysisResult);

    // ID로 분석 결과 조회
    Optional<AnalysisResult> findById(UUID id);

    // 프로젝트의 분석 목록 조회
    List<AnalysisResult> findByProjectId(UUID projectId);

    // 프로젝트의 최신 분석 조회
    Optional<AnalysisResult> findLatestByProjectId(UUID projectId);

    // 특정 브랜치의 가장 최근 완료 분석 조회
    Optional<AnalysisResult> findLatestByProjectIdAndBranch(UUID projectId, String branch);

    // ID 목록으로 분석 결과 일괄 조회 — N+1 방지용 배치 조회
    List<AnalysisResult> findAllById(List<UUID> ids);

    // 어드민 전용 — 전체 분석 횟수 조회
    long count();
}
