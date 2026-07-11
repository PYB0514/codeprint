// GitHub 레포 목록 응답 뷰 — infrastructure.github.GitHubRepoDto를 Controller에 그대로 노출하지 않기 위한 응용 계층 값 객체
package com.codeprint.application.project;

public record GithubRepoView(
        String name,
        String fullName,
        String htmlUrl,
        String description,
        boolean isPrivate
) {}
