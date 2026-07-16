// PrWebhookTargetPort 어댑터 — project·user 컨텍스트를 브리지해 repo를 리뷰 대상으로 역해석
package com.codeprint.infrastructure.adapter;

import com.codeprint.application.project.ProjectQueryService;
import com.codeprint.application.user.UserQueryService;
import com.codeprint.domain.analysis.WebhookSignatureVerifier;
import com.codeprint.domain.analysis.port.PrWebhookTargetPort;
import com.codeprint.domain.project.Project;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class PrWebhookTargetAdapter implements PrWebhookTargetPort {

    private final ProjectQueryService projectQueryService;
    private final UserQueryService userQueryService;

    // owner/repo로 프로젝트를 찾고, 프로젝트별 시크릿으로 서명이 검증되며 GitHub 토큰을 가진 소유자가 있는 가장 오래된 프로젝트를 리뷰 대상으로 선택
    @Override
    public Optional<Target> resolve(String ownerRepo, byte[] rawBody, String signatureHeader) {
        String httpsUrl = "https://github.com/" + ownerRepo;
        List<Project> candidates = projectQueryService.findByRepoUrl(httpsUrl);
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        return candidates.stream()
                .sorted(Comparator.comparing(Project::getCreatedAt))
                .filter(p -> WebhookSignatureVerifier.verify(p.getWebhookSecret(), rawBody, signatureHeader))
                .map(this::toTargetIfTokenPresent)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }

    // 프로젝트 소유자가 GitHub 토큰을 보유한 경우에만 리뷰 대상으로 변환
    private Optional<Target> toTargetIfTokenPresent(Project project) {
        return userQueryService.findGithubAccessToken(project.getUserId())
                .filter(token -> !token.isBlank())
                .map(token -> new Target(project.getId(), project.getUserId(), token));
    }
}
