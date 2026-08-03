// 프로젝트 생성/삭제 및 플랜별 제한 처리 애플리케이션 서비스
package com.codeprint.application.project;

import com.codeprint.domain.project.Project;
import com.codeprint.domain.project.ProjectRepository;
import com.codeprint.domain.project.port.GraphWarningsCachePort;
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

    // 계정당 비공개 프로젝트 상한 — 신규 프로젝트는 항상 비공개로 생성되므로(Project.create) 사실상 계정당 상한과 동일.
    // 공개 프로젝트는 제외(무제한) — 저장비용은 무료 티어 리스크지만 공개 프로젝트는 커뮤니티 노출·성장에 기여하는 자산이라 별도 취급(codeprint_152/153 결정)
    private static final int MAX_PRIVATE_PROJECTS = 6;

    private final ProjectRepository projectRepository;
    private final GraphWarningsCachePort graphWarningsCachePort;

    // URL 유효성 검사·비공개 프로젝트 상한 확인 후 프로젝트 생성
    public Project createProject(UUID userId, String githubRepoUrl, String name, String description) {
        if (!GITHUB_URL_PATTERN.matcher(githubRepoUrl).matches()) {
            throw new IllegalArgumentException("Invalid GitHub repository URL: " + githubRepoUrl);
        }
        if (projectRepository.countPrivateByUserId(userId) >= MAX_PRIVATE_PROJECTS) {
            throw new IllegalStateException(
                    "비공개 프로젝트는 최대 " + MAX_PRIVATE_PROJECTS + "개까지 만들 수 있습니다. 기존 프로젝트를 공개로 전환하거나 삭제한 뒤 다시 시도해주세요.");
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

    // 소유자 확인 후 게이트 정책(AUTO/DDD/LAYERED) 전환 — 자동감지와 무관하게 지정 방향의 게이트 규칙 강제 적용.
    // gatePolicy는 GraphQueryService.getWarnings()의 detect() 계산에 직접 반영되는데 캐시 키(graphId)엔 없어,
    // 무효화하지 않으면 최대 10분간 이전 정책 기준 경고로 PR 게이트가 오판정한다.
    public Project setGatePolicy(UUID projectId, UUID requestingUserId, GatePolicy policy) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        if (!project.getUserId().equals(requestingUserId)) {
            throw new IllegalStateException("Not authorized to modify this project");
        }
        project.setGatePolicy(policy);
        Project saved = projectRepository.save(project);
        graphWarningsCachePort.evictAll();
        return saved;
    }

    // 소유자 확인 후 국소분석 스코프 설정 (null이면 해제 — 레포 전체로 되돌림)
    public Project setPathPrefix(UUID projectId, UUID requestingUserId, String pathPrefix) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        if (!project.getUserId().equals(requestingUserId)) {
            throw new IllegalStateException("Not authorized to modify this project");
        }
        project.setPathPrefix(pathPrefix);
        return projectRepository.save(project);
    }

    // 소유자 확인 후 AI 컨텍스트 내보내기 차단 토글(DLP) — 코드가 외부 LLM으로 전송되는 걸 원천 금지
    public Project setAiExportDisabled(UUID projectId, UUID requestingUserId, boolean disabled) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        if (!project.getUserId().equals(requestingUserId)) {
            throw new IllegalStateException("Not authorized to modify this project");
        }
        project.setAiExportDisabled(disabled);
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
