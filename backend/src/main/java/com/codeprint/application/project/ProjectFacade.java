// project 컨트롤러가 analysis 컨텍스트를 직접 주입하지 않도록 조율하는 Facade
package com.codeprint.application.project;

import com.codeprint.application.analysis.AnalysisApplicationService;
import com.codeprint.infrastructure.github.GitHubApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectFacade {

    private final ProjectQueryService projectQueryService;
    private final AnalysisApplicationService analysisApplicationService;
    private final GitHubApiClient gitHubApiClient;

    // 사용자의 GitHub 레포 목록 조회 (프로젝트 생성 시 선택용) — 토큰 없거나 조회 실패 시 빈 목록
    public List<GithubRepoView> getGithubRepos(String githubToken) {
        if (githubToken == null || githubToken.isBlank()) return List.of();
        try {
            return gitHubApiClient.fetchUserRepos(githubToken).stream()
                    .map(r -> new GithubRepoView(r.name(), r.fullName(), r.htmlUrl(), r.description(), r.isPrivate()))
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    // 프로젝트 레포의 GitHub 브랜치 목록 조회
    public List<String> getBranches(UUID projectId, UUID userId, String githubToken) {
        var project = projectQueryService.getProject(projectId, userId);
        return gitHubApiClient.fetchBranches(project.getGithubRepoUrl(), githubToken);
    }

    // 최신 분석과 GitHub 최신 커밋을 비교하여 재분석 필요 여부 반환
    public Map<String, Object> getFreshness(UUID projectId, UUID userId, String githubToken) {
        var project = projectQueryService.getProject(projectId, userId);
        var latestAnalysis = analysisApplicationService.getLatestAnalysis(projectId);

        if (latestAnalysis.isEmpty() || latestAnalysis.get().getLastCommitSha() == null) {
            return Map.of("isOutdated", false, "reason", "no_data");
        }

        var analysis = latestAnalysis.get();
        String branch = analysis.getBranch() != null ? analysis.getBranch() : "main";

        try {
            String latestSha = gitHubApiClient.fetchLatestCommitSha(
                    project.getGithubRepoUrl(), branch, githubToken);
            boolean isOutdated = !latestSha.equals(analysis.getLastCommitSha());
            return Map.of(
                    "isOutdated", isOutdated,
                    "branch", branch,
                    "lastAnalyzedAt", analysis.getFinishedAt().toString(),
                    "lastCommitSha", analysis.getLastCommitSha(),
                    "latestCommitSha", latestSha
            );
        } catch (Exception e) {
            log.warn("Freshness 조회 실패 projectId={} token_null={} cause={}",
                    projectId, githubToken == null, e.getMessage(), e);
            String causeMsg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            String reason = causeMsg != null && causeMsg.contains("403") ? "rate_limit" : "github_error";
            return Map.of("isOutdated", false, "reason", reason);
        }
    }

    // 주요 브랜치 기준으로 freshness 조회
    public Map<String, Object> getPrimaryFreshness(UUID projectId, UUID userId, String githubToken) {
        var project = projectQueryService.getProject(projectId, userId);
        String primaryBranch = project.getPrimaryBranch();
        if (primaryBranch == null) {
            return Map.of("isOutdated", false, "reason", "not_set");
        }

        var latestAnalysis = analysisApplicationService.getLatestAnalysisByBranch(projectId, primaryBranch);
        if (latestAnalysis.isEmpty() || latestAnalysis.get().getLastCommitSha() == null) {
            return Map.of("isOutdated", false, "reason", "no_data", "branch", primaryBranch);
        }

        var analysis = latestAnalysis.get();
        try {
            String latestSha = gitHubApiClient.fetchLatestCommitSha(
                    project.getGithubRepoUrl(), primaryBranch, githubToken);
            boolean isOutdated = !latestSha.equals(analysis.getLastCommitSha());
            return Map.of(
                    "isOutdated", isOutdated,
                    "branch", primaryBranch,
                    "lastAnalyzedAt", analysis.getFinishedAt().toString(),
                    "lastCommitSha", analysis.getLastCommitSha(),
                    "latestCommitSha", latestSha
            );
        } catch (Exception e) {
            log.warn("PrimaryFreshness 조회 실패 projectId={} token_null={} cause={}",
                    projectId, githubToken == null, e.getMessage(), e);
            String causeMsg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            String reason = causeMsg != null && causeMsg.contains("403") ? "rate_limit" : "github_error";
            return Map.of("isOutdated", false, "reason", reason, "branch", primaryBranch);
        }
    }
}
