// Community ProjectReadPort의 project 컨텍스트 어댑터 — 소유·공개 검증 후 레포 URL 반환
package com.codeprint.infrastructure.adapter;

import com.codeprint.application.project.ProjectQueryService;
import com.codeprint.domain.community.port.ProjectReadPort;
import com.codeprint.domain.project.Project;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ProjectReadAdapter implements ProjectReadPort {

    private final ProjectQueryService projectQueryService;

    // 소유자 검증 통과 + 공개 프로젝트면 레포 URL 반환, 그 외(미소유·비공개·부재)는 empty
    @Override
    public Optional<String> findPublicRepoUrl(UUID projectId, UUID userId) {
        try {
            Project project = projectQueryService.getProject(projectId, userId);
            return project.isPublic()
                    ? Optional.ofNullable(project.getGithubRepoUrl())
                    : Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
