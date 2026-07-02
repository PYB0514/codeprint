// Featured RepoMetadataPort의 GitHub API 어댑터 — 실패해도 갱신 자체는 막지 않도록 예외를 흡수
package com.codeprint.infrastructure.adapter;

import com.codeprint.domain.featured.port.RepoMetadataPort;
import com.codeprint.infrastructure.github.GitHubApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FeaturedRepoMetadataAdapter implements RepoMetadataPort {

    private final GitHubApiClient gitHubApiClient;

    @Override
    public RepoMetadata fetch(String repoFullName) {
        try {
            GitHubApiClient.RepoMetadata metadata = gitHubApiClient.fetchRepoMetadata(repoFullName);
            return new RepoMetadata(metadata.stars(), metadata.description());
        } catch (Exception e) {
            log.warn("레포 메타데이터 조회 실패 (무시): {}", repoFullName, e);
            return new RepoMetadata(null, null);
        }
    }
}
