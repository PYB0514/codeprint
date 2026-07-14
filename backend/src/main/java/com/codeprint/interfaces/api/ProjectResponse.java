// 프로젝트 API 응답 DTO
package com.codeprint.interfaces.api;

import com.codeprint.domain.project.Project;

import java.time.Instant;
import java.util.UUID;

public record ProjectResponse(
        UUID id,
        String name,
        String description,
        String githubRepoUrl,
        boolean isPublic,
        Instant createdAt,
        String primaryBranch,
        boolean gateArchitectureEnabled,
        boolean gateExperimentalEnabled
) {
    // 프로젝트 도메인 객체를 응답 DTO로 변환
    public static ProjectResponse from(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getGithubRepoUrl(),
                project.isPublic(),
                project.getCreatedAt(),
                project.getPrimaryBranch(),
                project.isGateArchitectureEnabled(),
                project.isGateExperimentalEnabled()
        );
    }
}
