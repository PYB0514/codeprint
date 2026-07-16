// 프로젝트 조회 애플리케이션 서비스 (읽기 전용)
package com.codeprint.application.project;

import com.codeprint.domain.project.Project;
import com.codeprint.domain.project.ProjectRepository;
import com.codeprint.domain.project.port.TeamAccessPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectQueryService {

    private final ProjectRepository projectRepository;
    private final TeamAccessPort teamAccessPort;

    // 사용자 ID로 해당 사용자의 전체 프로젝트 목록 조회
    public List<Project> getProjectsByUser(UUID userId) {
        return projectRepository.findByUserId(userId);
    }

    // GitHub 레포 URL로 프로젝트 목록 조회 — webhook이 repo로 프로젝트를 역해석할 때 사용
    public List<Project> findByRepoUrl(String repoHttpsUrl) {
        return projectRepository.findByRepoUrl(repoHttpsUrl);
    }

    // 소유자 또는 팀 접근 권한(RBAC) 확인 후 단일 프로젝트 조회
    public Project getProject(UUID projectId, UUID requestingUserId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        boolean isOwner = project.getUserId().equals(requestingUserId);
        if (!isOwner && !teamAccessPort.hasAccessViaTeam(projectId, requestingUserId)) {
            throw new IllegalStateException("Not authorized to access this project");
        }
        return project;
    }

    // 내부 시스템 호출 전용 조회(소유권 검증 없음) — PR 게이트처럼 앞단에서 이미 소유권을 검증한 흐름에서만 사용
    public Project getProjectInternal(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
    }

    // 내부 시스템 호출 전용 — PR 게이트가 연결된 프로젝트 전체 조회 (G-5 리컨실리에이션 cron 전용)
    public List<Project> getAllPrGateConnectedInternal() {
        return projectRepository.findAllWithPrGateConnected();
    }

    // 공개 프로젝트 조회 (비인증 허용)
    public Project getPublicProject(UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        if (!project.isPublic()) {
            throw new IllegalStateException("Project is not public");
        }
        return project;
    }
}
