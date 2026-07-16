// 프로젝트 생성/삭제 및 플랜별 제한 처리 애플리케이션 서비스
package com.codeprint.application.project;

import com.codeprint.domain.project.Project;
import com.codeprint.domain.project.ProjectRepository;
import com.codeprint.shared.gate.GatePolicy;
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

    // URL 유효성 검사 후 프로젝트 생성
    public Project createProject(UUID userId, String githubRepoUrl, String name, String description) {
        if (!GITHUB_URL_PATTERN.matcher(githubRepoUrl).matches()) {
            throw new IllegalArgumentException("Invalid GitHub repository URL: " + githubRepoUrl);
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

    // 소유자 확인 후 주요 브랜치 설정 (null이면 해제)
    public Project setPrimaryBranch(UUID projectId, UUID requestingUserId, String branch) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        if (!project.getUserId().equals(requestingUserId)) {
            throw new IllegalStateException("Not authorized to modify this project");
        }
        project.setPrimaryBranch(branch);
        return projectRepository.save(project);
    }

    // 소유자 확인 후 PR 게이트 등급(1단계 architecture·2단계 experimental) 설정 변경
    public Project setGateSettings(UUID projectId, UUID requestingUserId,
                                    boolean architectureEnabled, boolean experimentalEnabled) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        if (!project.getUserId().equals(requestingUserId)) {
            throw new IllegalStateException("Not authorized to modify this project");
        }
        project.setGateArchitectureEnabled(architectureEnabled);
        project.setGateExperimentalEnabled(experimentalEnabled);
        return projectRepository.save(project);
    }

    // 소유자 확인 후 PR 게이트 webhook 연결(시크릿이 없을 때만 신규 발급 — 이미 연결돼 있으면 그대로 반환)
    public Project connectPrGate(UUID projectId, UUID requestingUserId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        if (!project.getUserId().equals(requestingUserId)) {
            throw new IllegalStateException("Not authorized to modify this project");
        }
        if (!project.isPrGateConnected()) {
            project.generateWebhookSecret();
            project = projectRepository.save(project);
        }
        return project;
    }

    // 소유자 확인 후 PR 게이트 webhook 시크릿 재발급(기존 시크릿 무효화)
    public Project rotatePrGateSecret(UUID projectId, UUID requestingUserId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        if (!project.getUserId().equals(requestingUserId)) {
            throw new IllegalStateException("Not authorized to modify this project");
        }
        project.generateWebhookSecret();
        return projectRepository.save(project);
    }

    // 소유자 확인 후 PR 게이트 연결 해제
    public Project disconnectPrGate(UUID projectId, UUID requestingUserId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        if (!project.getUserId().equals(requestingUserId)) {
            throw new IllegalStateException("Not authorized to modify this project");
        }
        project.disconnectPrGate();
        return projectRepository.save(project);
    }

    // 소유자 확인 후 게이트 정책(AUTO/DDD/LAYERED) 전환 — 자동감지와 무관하게 지정 방향의 게이트 규칙 강제 적용
    public Project setGatePolicy(UUID projectId, UUID requestingUserId, GatePolicy policy) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        if (!project.getUserId().equals(requestingUserId)) {
            throw new IllegalStateException("Not authorized to modify this project");
        }
        project.setGatePolicy(policy);
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
