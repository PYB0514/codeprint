// 분석 결과 도메인 Repository JPA 구현체
package com.codeprint.infrastructure.persistence.analysis;

import com.codeprint.domain.analysis.AnalysisRepository;
import com.codeprint.domain.analysis.AnalysisResult;
import com.codeprint.domain.analysis.AnalysisStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class AnalysisRepositoryImpl implements AnalysisRepository {

    private final AnalysisJpaRepository jpa;

    // 분석 결과 엔티티를 저장하고 반환
    @Override
    public AnalysisResult save(AnalysisResult analysisResult) {
        return jpa.save(analysisResult);
    }

    // UUID로 분석 결과 조회
    @Override
    public Optional<AnalysisResult> findById(UUID id) {
        return jpa.findById(id);
    }

    // 프로젝트 ID로 분석 결과 목록 조회
    @Override
    public List<AnalysisResult> findByProjectId(UUID projectId) {
        return jpa.findByProjectId(projectId);
    }

    // 프로젝트의 가장 최근 분석 결과 조회
    @Override
    public Optional<AnalysisResult> findLatestByProjectId(UUID projectId) {
        return jpa.findLatestByProjectId(projectId);
    }

    // 특정 브랜치의 가장 최근 분석 결과 조회
    @Override
    public Optional<AnalysisResult> findLatestByProjectIdAndBranch(UUID projectId, String branch) {
        return jpa.findLatestByProjectIdAndBranch(projectId, branch);
    }

    // ID 목록으로 분석 결과 일괄 조회 — Spring Data findAllById 활용
    @Override
    public List<AnalysisResult> findAllById(List<UUID> ids) {
        return jpa.findAllById(ids);
    }

    // 상태 목록에 해당하는 분석 결과 조회
    @Override
    public List<AnalysisResult> findByStatusIn(List<AnalysisStatus> statuses) {
        return jpa.findByStatusIn(statuses);
    }

    // 전체 분석 횟수 반환
    @Override
    public long count() {
        return jpa.count();
    }
}
