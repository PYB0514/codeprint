// 프로젝트 CRUD REST API 컨트롤러
package com.codeprint.interfaces.api;

import com.codeprint.application.project.ProjectCommandService;
import com.codeprint.application.project.ProjectQueryService;
import com.codeprint.domain.user.User;
import com.codeprint.infrastructure.github.GitHubApiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectCommandService projectCommandService;
    private final ProjectQueryService projectQueryService;
    private final GitHubApiClient gitHubApiClient;

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
        return ResponseEntity.ok(gitHubApiClient.fetchBranches(project.getGithubRepoUrl()));
    }

    // 프로젝트 삭제
    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> deleteProject(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal User user) {
        projectCommandService.deleteProject(projectId, user.getId());
        return ResponseEntity.noContent().build();
    }

    public record CreateProjectRequest(String githubRepoUrl, String name, String description) {}
}
