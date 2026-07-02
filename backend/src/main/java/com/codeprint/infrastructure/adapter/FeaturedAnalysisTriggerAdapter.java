// Featured AnalysisTriggerPort의 analysis 컨텍스트 어댑터 — 인증 토큰 없이 비동기 분석 시작
package com.codeprint.infrastructure.adapter;

import com.codeprint.application.analysis.AnalysisRunner;
import com.codeprint.domain.analysis.AnalysisRepository;
import com.codeprint.domain.analysis.AnalysisResult;
import com.codeprint.domain.featured.port.AnalysisTriggerPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class FeaturedAnalysisTriggerAdapter implements AnalysisTriggerPort {

    private final AnalysisRepository analysisRepository;
    private final AnalysisRunner analysisRunner;

    // 기본 브랜치·비인증(공개 레포 전용)으로 분석 레코드 생성 후 비동기 실행
    @Override
    @Transactional
    public void triggerAnalysis(UUID projectId, String githubRepoUrl) {
        AnalysisResult analysis = AnalysisResult.create(projectId, null);
        analysisRepository.save(analysis);
        analysisRunner.run(analysis.getId(), projectId, githubRepoUrl, null, null);
    }
}
