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

    // 프로젝트 소유권 확인 후 레포 URL 반환 — PR 리뷰가 project 컨텍스트를 직접 의존하지 않도록 경유
    public String resolveOwnedRepoUrl(UUID projectId, UUID userId) {
        return projectQueryService.getProject(projectId, userId).getGithubRepoUrl();
    }

    // PR 게이트 등급 판정용 프로젝트 설정 조회 — review() 흐름에서 이미 소유권을 검증했으므로 내부 전용 조회 사용
    public ProjectGateSettings getGateSettings(UUID projectId) {
        var project = projectQueryService.getProjectInternal(projectId);
        return new ProjectGateSettings(project.isGateArchitectureEnabled(), project.isGateExperimentalEnabled());
    }

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
