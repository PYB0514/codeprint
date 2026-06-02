// 프로젝트 CRUD REST API 컨트롤러
package com.codeprint.interfaces.api;

import com.codeprint.application.project.ProjectCommandService;
import com.codeprint.domain.project.Project;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectCommandService projectCommandService;

    @GetMapping
    public ResponseEntity<List<Project>> getProjects(@RequestParam UUID userId) {
        return ResponseEntity.ok(projectCommandService.getProjectsByUser(userId));
    }

    @PostMapping
    public ResponseEntity<Project> createProject(@RequestBody CreateProjectRequest request) {
        Project project = projectCommandService.createProject(
                request.userId(), request.githubRepoUrl(), request.name(), request.description());
        return ResponseEntity.ok(project);
    }

    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> deleteProject(@PathVariable UUID projectId,
                                              @RequestParam UUID userId) {
        projectCommandService.deleteProject(projectId, userId);
        return ResponseEntity.noContent().build();
    }

    public record CreateProjectRequest(UUID userId, String githubRepoUrl, String name, String description) {}
}
