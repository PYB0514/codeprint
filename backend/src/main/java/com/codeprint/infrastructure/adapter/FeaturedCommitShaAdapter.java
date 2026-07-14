// Featured CommitShaPort의 GitHub API 어댑터 — 실패해도 재분석 판단만 안전 쪽(스킵 안 함)으로 흡수
package com.codeprint.infrastructure.adapter;

import com.codeprint.domain.featured.port.CommitShaPort;
import com.codeprint.infrastructure.github.GitHubApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class FeaturedCommitShaAdapter implements CommitShaPort {

    private final GitHubApiClient gitHubApiClient;

    @Override
    public Optional<String> fetchLatestCommitSha(String repoFullName) {
        try {
            return Optional.of(gitHubApiClient.fetchLatestCommitShaOfDefaultBranch(repoFullName));
        } catch (Exception e) {
            log.warn("커밋 SHA 조회 실패 (무시, 안전하게 재분석): {}", repoFullName, e);
            return Optional.empty();
        }
    }
}
