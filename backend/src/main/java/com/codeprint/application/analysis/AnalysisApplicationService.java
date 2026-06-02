// 코드 분석 시작 및 조회 애플리케이션 서비스
package com.codeprint.application.analysis;

import com.codeprint.domain.analysis.AnalysisRepository;
import com.codeprint.domain.analysis.AnalysisResult;
import com.codeprint.domain.project.Project;
import com.codeprint.domain.project.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class AnalysisApplicationService {

    private final AnalysisRepository analysisRepository;
    private final ProjectRepository projectRepository;
    private final AnalysisRunner analysisRunner;

    // 분석 레코드를 생성하고 비동기 분석을 시작
    public AnalysisResult startAnalysis(UUID projectId, String branch) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

        AnalysisResult analysis = AnalysisResult.create(projectId, branch);
        analysisRepository.save(analysis);

        // URL을 미리 추출해서 넘김 — 트랜잭션 커밋 전 비동기 스레드가 DB 조회 시 못 찾는 문제 방지
        analysisRunner.run(analysis.getId(), projectId, project.getGithubRepoUrl(), branch);
        return analysis;
    }

    // 분석 ID로 분석 결과를 조회
    @Transactional(readOnly = true)
    public AnalysisResult getAnalysis(UUID analysisId) {
        return analysisRepository.findById(analysisId)
                .orElseThrow(() -> new IllegalArgumentException("Analysis not found: " + analysisId));
    }
}
