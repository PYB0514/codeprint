package com.codeprint.application.project;

import com.codeprint.domain.project.Project;
import com.codeprint.domain.project.ProjectLimit;
import com.codeprint.domain.project.ProjectRepository;
import com.codeprint.domain.user.User;
import com.codeprint.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ProjectCommandService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    public Project createProject(UUID userId, String githubRepoUrl, String name, String description) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        ProjectLimit limit = ProjectLimit.of(user.getPlan());
        int currentCount = projectRepository.countByUserId(userId);
        if (limit.isExceeded(currentCount)) {
            throw new IllegalStateException("Project limit reached for plan: " + user.getPlan());
        }

        Project project = Project.create(userId, githubRepoUrl, name, description);
        return projectRepository.save(project);
    }

    public void deleteProject(UUID projectId, UUID requestingUserId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        if (!project.getUserId().equals(requestingUserId)) {
            throw new IllegalStateException("Not authorized to delete this project");
        }
        projectRepository.deleteById(projectId);
    }

    @Transactional(readOnly = true)
    public List<Project> getProjectsByUser(UUID userId) {
        return projectRepository.findByUserId(userId);
    }
}
