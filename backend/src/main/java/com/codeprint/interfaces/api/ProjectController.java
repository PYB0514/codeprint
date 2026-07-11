// 프로젝트 CRUD REST API 컨트롤러
package com.codeprint.interfaces.api;

import com.codeprint.application.project.GithubRepoView;
import com.codeprint.application.project.ProjectCommandService;
import com.codeprint.application.project.ProjectFacade;
import com.codeprint.application.project.ProjectQueryService;
import com.codeprint.domain.user.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
    private final ProjectFacade projectFacade;

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
                        user.getId(), request.githubRepoUrl(), request.name(), request.description()));
        return ResponseEntity.status(201).body(response);
    }

    // 현재 사용자의 GitHub 레포 목록 조회 (프로젝트 생성 시 선택용)
    @GetMapping("/github-repos")
    public ResponseEntity<List<GithubRepoView>> getGithubRepos(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(projectFacade.getGithubRepos(user.getGithubAccessToken()));
    }

    // 프로젝트 레포의 GitHub 브랜치 목록 조회
    @GetMapping("/{projectId}/branches")
    public ResponseEntity<List<String>> getBranches(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(projectFacade.getBranches(projectId, user.getId(), user.getGithubAccessToken()));
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
        return ResponseEntity.ok(projectFacade.getFreshness(projectId, user.getId(), user.getGithubAccessToken()));
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
        return ResponseEntity.ok(projectFacade.getPrimaryFreshness(projectId, user.getId(), user.getGithubAccessToken()));
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
