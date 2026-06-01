package com.codeprint.infrastructure.persistence.analysis;

import com.codeprint.domain.analysis.AnalysisRepository;
import com.codeprint.domain.analysis.AnalysisResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class AnalysisRepositoryImpl implements AnalysisRepository {

    private final AnalysisJpaRepository jpa;

    @Override
    public AnalysisResult save(AnalysisResult analysisResult) {
        return jpa.save(analysisResult);
    }

    @Override
    public Optional<AnalysisResult> findById(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public List<AnalysisResult> findByProjectId(UUID projectId) {
        return jpa.findByProjectId(projectId);
    }

    @Override
    public Optional<AnalysisResult> findLatestByProjectId(UUID projectId) {
        return jpa.findLatestByProjectId(projectId);
    }
}
