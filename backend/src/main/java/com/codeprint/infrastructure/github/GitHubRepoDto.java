// GitHub 레포 목록 API 응답 DTO
package com.codeprint.infrastructure.github;

public record GitHubRepoDto(
        String name,
        String fullName,
        String htmlUrl,
        String description,
        boolean isPrivate
) {}
