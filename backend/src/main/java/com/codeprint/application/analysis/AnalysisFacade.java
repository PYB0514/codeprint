// analysis 컨트롤러가 project 컨텍스트를 직접 주입하지 않도록 조율하는 Facade
package com.codeprint.application.analysis;

import com.codeprint.application.project.ProjectQueryService;
import com.codeprint.domain.analysis.AnalysisResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AnalysisFacade {

    private final AnalysisApplicationService analysisApplicationService;
    private final ProjectQueryService projectQueryService;

    // 프로젝트 소유권 확인 후 분석 시작 — analysisId/status/progress 반환
    public Map<String, Object> startAnalysis(UUID projectId, String branch, UUID userId, String githubToken) {
        var project = projectQueryService.getProject(projectId, userId);
        AnalysisResult result = analysisApplicationService.startAnalysis(
                projectId, branch, project.getGithubRepoUrl(), githubToken);
        return Map.of(
                "analysisId", result.getId(),
                "status", result.getStatus(),
                "progress", result.getProgress()
        );
    }

    // 분석 소유권 확인 후 상태 조회 — analysisId/status/progress/errorMsg 반환
    public Map<String, Object> getAnalysis(UUID analysisId, UUID userId) {
        AnalysisResult result = analysisApplicationService.getAnalysis(analysisId);
        projectQueryService.getProject(result.getProjectId(), userId);
        return Map.of(
                "analysisId", result.getId(),
                "status", result.getStatus(),
                "progress", result.getProgress(),
                "errorMsg", result.getErrorMsg() != null ? result.getErrorMsg() : ""
        );
    }
}
