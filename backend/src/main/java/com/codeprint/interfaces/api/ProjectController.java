// 프로젝트 CRUD REST API 컨트롤러
package com.codeprint.interfaces.api;

import com.codeprint.application.analysis.AnalysisApplicationService;
import com.codeprint.application.project.ProjectCommandService;
import com.codeprint.application.project.ProjectQueryService;
import com.codeprint.domain.user.User;
import com.codeprint.infrastructure.github.GitHubApiClient;
import com.codeprint.infrastructure.github.GitHubRepoDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
@Slf4j
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectCommandService projectCommandService;
    private final ProjectQueryService projectQueryService;
    private final GitHubApiClient gitHubApiClient;
    private final AnalysisApplicationService analysisApplicationService;

    // 현재 사용자의 프로젝트 목록 조회
    @GetMapping
    public ResponseEntity<List<ProjectResponse>> getProjects(@AuthenticationPrincipal User user) {
        List<ProjectResponse> projects = projectQueryService.getProjectsByUser(user.getId())
                .stream()
                .map(ProjectResponse::from)
                .toList();
        return ResponseEntity.ok(projects);
    }

    // 단일 프로젝트 조회
    @GetMapping("/{projectId}")
    public ResponseEntity<ProjectResponse> getProject(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(
                ProjectResponse.from(projectQueryService.getProject(projectId, user.getId())));
    }

    // 새 프로젝트 생성
    @PostMapping
    public ResponseEntity<ProjectResponse> createProject(
            @Valid @RequestBody CreateProjectRequest request,
            @AuthenticationPrincipal User user) {
        ProjectResponse response = ProjectResponse.from(
                projectCommandService.createProject(
                        user.getId(), request.githubRepoUrl(), request.name(), request.description(),
                        user.getPlan().maxProjects()));
        return ResponseEntity.status(201).body(response);
    }

    // 현재 사용자의 GitHub 레포 목록 조회 (프로젝트 생성 시 선택용)
    @GetMapping("/github-repos")
    public ResponseEntity<List<GitHubRepoDto>> getGithubRepos(@AuthenticationPrincipal User user) {
        String token = user.getGithubAccessToken();
        if (token == null || token.isBlank()) return ResponseEntity.ok(List.of());
        try {
            return ResponseEntity.ok(gitHubApiClient.fetchUserRepos(token));
        } catch (Exception e) {
            return ResponseEntity.ok(List.of());
        }
    }

    // 프로젝트 레포의 GitHub 브랜치 목록 조회
    @GetMapping("/{projectId}/branches")
    public ResponseEntity<List<String>> getBranches(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal User user) {
        var project = projectQueryService.getProject(projectId, user.getId());
        return ResponseEntity.ok(gitHubApiClient.fetchBranches(project.getGithubRepoUrl(), user.getGithubAccessToken()));
    }

    // 프로젝트 공개/비공개 상태 전환
    @PatchMapping("/{projectId}/visibility")
    public ResponseEntity<ProjectResponse> updateVisibility(
            @PathVariable UUID projectId,
            @RequestBody VisibilityRequest request,
            @AuthenticationPrincipal User user) {
        ProjectResponse response = ProjectResponse.from(
                projectCommandService.toggleVisibility(projectId, user.getId(), request.isPublic()));
        return ResponseEntity.ok(response);
    }

    // 최신 분석 버전과 GitHub 최신 커밋을 비교하여 재분석 필요 여부 반환
    @GetMapping("/{projectId}/freshness")
    public ResponseEntity<Map<String, Object>> getFreshness(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal User user) {
        var project = projectQueryService.getProject(projectId, user.getId());
        var latestAnalysis = analysisApplicationService.getLatestAnalysis(projectId);

        if (latestAnalysis.isEmpty() || latestAnalysis.get().getLastCommitSha() == null) {
            return ResponseEntity.ok(Map.of("isOutdated", false, "reason", "no_data"));
        }

        var analysis = latestAnalysis.get();
        String branch = analysis.getBranch() != null ? analysis.getBranch() : "main";

        try {
            String latestSha = gitHubApiClient.fetchLatestCommitSha(
                    project.getGithubRepoUrl(), branch, user.getGithubAccessToken());
            boolean isOutdated = !latestSha.equals(analysis.getLastCommitSha());
            return ResponseEntity.ok(Map.of(
                    "isOutdated", isOutdated,
                    "branch", branch,
                    "lastAnalyzedAt", analysis.getFinishedAt().toString(),
                    "lastCommitSha", analysis.getLastCommitSha(),
                    "latestCommitSha", latestSha
            ));
        } catch (Exception e) {
            log.warn("Freshness 조회 실패 projectId={} token_null={} cause={}", projectId, user.getGithubAccessToken() == null, e.getMessage(), e);
            String causeMsg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            String reason = causeMsg != null && causeMsg.contains("403") ? "rate_limit" : "github_error";
            return ResponseEntity.ok(Map.of("isOutdated", false, "reason", reason));
        }
    }

    // 주요 브랜치 설정 (null이면 해제)
    @PatchMapping("/{projectId}/primary-branch")
    public ResponseEntity<ProjectResponse> setPrimaryBranch(
            @PathVariable UUID projectId,
            @RequestBody PrimaryBranchRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ProjectResponse.from(
                projectCommandService.setPrimaryBranch(projectId, user.getId(), request.branch())));
    }

    // 주요 브랜치 freshness 조회 (primary branch 기준)
    @GetMapping("/{projectId}/primary-freshness")
    public ResponseEntity<Map<String, Object>> getPrimaryFreshness(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal User user) {
        var project = projectQueryService.getProject(projectId, user.getId());
        String primaryBranch = project.getPrimaryBranch();
        if (primaryBranch == null) {
            return ResponseEntity.ok(Map.of("isOutdated", false, "reason", "not_set"));
        }

        var latestAnalysis = analysisApplicationService.getLatestAnalysisByBranch(projectId, primaryBranch);
        if (latestAnalysis.isEmpty() || latestAnalysis.get().getLastCommitSha() == null) {
            return ResponseEntity.ok(Map.of("isOutdated", false, "reason", "no_data", "branch", primaryBranch));
        }

        var analysis = latestAnalysis.get();
        try {
            String latestSha = gitHubApiClient.fetchLatestCommitSha(
                    project.getGithubRepoUrl(), primaryBranch, user.getGithubAccessToken());
            boolean isOutdated = !latestSha.equals(analysis.getLastCommitSha());
            return ResponseEntity.ok(Map.of(
                    "isOutdated", isOutdated,
                    "branch", primaryBranch,
                    "lastAnalyzedAt", analysis.getFinishedAt().toString(),
                    "lastCommitSha", analysis.getLastCommitSha(),
                    "latestCommitSha", latestSha
            ));
        } catch (Exception e) {
            log.warn("PrimaryFreshness 조회 실패 projectId={} token_null={} cause={}", projectId, user.getGithubAccessToken() == null, e.getMessage(), e);
            String causeMsg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            String reason = causeMsg != null && causeMsg.contains("403") ? "rate_limit" : "github_error";
            return ResponseEntity.ok(Map.of("isOutdated", false, "reason", reason, "branch", primaryBranch));
        }
    }

    // 프로젝트 삭제
    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> deleteProject(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal User user) {
        projectCommandService.deleteProject(projectId, user.getId());
        return ResponseEntity.noContent().build();
    }

    // 프로젝트 생성 요청 DTO (레포 URL, 이름, 설명)
    public record CreateProjectRequest(
            @NotBlank String githubRepoUrl,
            @NotBlank String name,
            String description) {}

    // 공개/비공개 전환 요청 DTO
    public record VisibilityRequest(boolean isPublic) {}

    // 주요 브랜치 설정 요청 DTO (null이면 해제)
    public record PrimaryBranchRequest(String branch) {}
}
