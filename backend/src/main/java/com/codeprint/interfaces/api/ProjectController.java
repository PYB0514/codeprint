// 프로젝트 CRUD REST API 컨트롤러
package com.codeprint.interfaces.api;

import com.codeprint.application.project.ProjectCommandService;
import com.codeprint.application.project.ProjectQueryService;
import com.codeprint.domain.analysis.AnalysisRepository;
import com.codeprint.domain.user.User;
import com.codeprint.infrastructure.github.GitHubApiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectCommandService projectCommandService;
    private final ProjectQueryService projectQueryService;
    private final GitHubApiClient gitHubApiClient;
    private final AnalysisRepository analysisRepository;

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
            @RequestBody CreateProjectRequest request,
            @AuthenticationPrincipal User user) {
        ProjectResponse response = ProjectResponse.from(
                projectCommandService.createProject(
                        user.getId(), request.githubRepoUrl(), request.name(), request.description()));
        return ResponseEntity.status(201).body(response);
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
        projectQueryService.getProject(projectId, user.getId());
        var project = projectQueryService.getProject(projectId, user.getId());
        var latestAnalysis = analysisRepository.findLatestByProjectId(projectId);

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
            return ResponseEntity.ok(Map.of("isOutdated", false, "reason", "github_error"));
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
    public record CreateProjectRequest(String githubRepoUrl, String name, String description) {}

    // 공개/비공개 전환 요청 DTO
    public record VisibilityRequest(boolean isPublic) {}
}
