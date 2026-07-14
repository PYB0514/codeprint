// Graph ProjectAccessPort의 project 컨텍스트 어댑터 — 소유·공개 검증 위임 + view 변환
package com.codeprint.infrastructure.adapter;

import com.codeprint.application.project.ProjectQueryService;
import com.codeprint.domain.graph.port.ProjectAccessPort;
import com.codeprint.domain.project.Project;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ProjectAccessAdapter implements ProjectAccessPort {

    private final ProjectQueryService projectQueryService;

    @Override
    public void verifyOwnership(UUID projectId, UUID userId) {
        projectQueryService.getProject(projectId, userId);
    }

    @Override
    public void verifyPublic(UUID projectId) {
        projectQueryService.getPublicProject(projectId);
    }

    @Override
    public ProjectAccessView getOwnedProject(UUID projectId, UUID userId) {
        return toView(projectQueryService.getProject(projectId, userId));
    }

    @Override
    public ProjectAccessView getPublicProject(UUID projectId) {
        return toView(projectQueryService.getPublicProject(projectId));
    }

    @Override
    public java.util.Optional<String> findGithubRepoUrl(UUID projectId) {
        try {
            return java.util.Optional.ofNullable(projectQueryService.getProjectInternal(projectId).getGithubRepoUrl());
        } catch (Exception e) {
            return java.util.Optional.empty();
        }
    }

    @Override
    public java.util.Optional<ProjectAccessView> getProjectById(UUID projectId) {
        try {
            return java.util.Optional.of(toView(projectQueryService.getProjectInternal(projectId)));
        } catch (Exception e) {
            return java.util.Optional.empty();
        }
    }

    // project 도메인 엔티티를 graph 도메인 소유 view로 변환 — 필요한 필드만 추림
    private ProjectAccessView toView(Project project) {
        return new ProjectAccessView(project.getId(), project.getUserId(), project.getName(), project.getGithubRepoUrl());
    }
}
