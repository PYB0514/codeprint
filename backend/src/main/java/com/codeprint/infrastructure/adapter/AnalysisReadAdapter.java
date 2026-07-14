// Graph AnalysisReadPort의 analysis 컨텍스트 어댑터 — 분석 조회 후 브랜치명 반환
package com.codeprint.infrastructure.adapter;

import com.codeprint.application.analysis.AnalysisApplicationService;
import com.codeprint.domain.graph.port.AnalysisReadPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AnalysisReadAdapter implements AnalysisReadPort {

    private final AnalysisApplicationService analysisApplicationService;

    @Override
    public Optional<String> findBranch(UUID analysisId) {
        try {
            return Optional.ofNullable(analysisApplicationService.getAnalysis(analysisId).getBranch());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> findCommitSha(UUID analysisId) {
        try {
            return Optional.ofNullable(analysisApplicationService.getAnalysis(analysisId).getLastCommitSha());
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
