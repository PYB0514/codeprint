// 프로젝트 생성/삭제 및 플랜별 제한 처리 애플리케이션 서비스
package com.codeprint.application.project;

import com.codeprint.domain.project.Project;
import com.codeprint.domain.project.ProjectLimit;
import com.codeprint.domain.project.ProjectRepository;
import com.codeprint.domain.user.User;
import com.codeprint.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Transactional
public class ProjectCommandService {

    private static final Pattern GITHUB_URL_PATTERN =
            Pattern.compile("^https://github\\.com/[\\w.-]+/[\\w.-]+/?$");

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    // URL 유효성 및 플랜 제한 검사 후 프로젝트 생성
    public Project createProject(UUID userId, String githubRepoUrl, String name, String description) {
        if (!GITHUB_URL_PATTERN.matcher(githubRepoUrl).matches()) {
            throw new IllegalArgumentException("Invalid GitHub repository URL: " + githubRepoUrl);
        }

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

    // 소유자 확인 후 공개/비공개 상태 전환
    public Project toggleVisibility(UUID projectId, UUID requestingUserId, boolean makePublic) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        if (!project.getUserId().equals(requestingUserId)) {
            throw new IllegalStateException("Not authorized to modify this project");
        }
        if (makePublic) {
            project.makePublic();
        } else {
            project.makePrivate();
        }
        return projectRepository.save(project);
    }

    // 소유자 확인 후 프로젝트 삭제
    public void deleteProject(UUID projectId, UUID requestingUserId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        if (!project.getUserId().equals(requestingUserId)) {
            throw new IllegalStateException("Not authorized to delete this project");
        }
        projectRepository.deleteById(projectId);
    }
}
