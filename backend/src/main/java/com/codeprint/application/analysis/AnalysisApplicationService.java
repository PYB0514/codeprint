package com.codeprint.application.analysis;

import com.codeprint.domain.analysis.AnalysisRepository;
import com.codeprint.domain.analysis.AnalysisResult;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class AnalysisApplicationService {

    private final AnalysisRepository analysisRepository;

    public AnalysisResult startAnalysis(UUID projectId) {
        AnalysisResult analysis = AnalysisResult.create(projectId);
        analysisRepository.save(analysis);
        runAnalysisAsync(analysis.getId());
        return analysis;
    }

    @Async
    public void runAnalysisAsync(UUID analysisId) {
        AnalysisResult analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new IllegalArgumentException("Analysis not found: " + analysisId));
        try {
            analysis.start();
            analysisRepository.save(analysis);
            // Tree-sitter 분석 로직은 infrastructure/treesitter에서 구현
            analysis.complete();
            analysisRepository.save(analysis);
        } catch (Exception e) {
            analysis.fail(e.getMessage());
            analysisRepository.save(analysis);
        }
    }

    @Transactional(readOnly = true)
    public AnalysisResult getAnalysis(UUID analysisId) {
        return analysisRepository.findById(analysisId)
                .orElseThrow(() -> new IllegalArgumentException("Analysis not found: " + analysisId));
    }
}
