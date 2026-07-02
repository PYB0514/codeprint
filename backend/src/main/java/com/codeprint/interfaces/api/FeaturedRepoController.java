// 오늘의 공개레포 공개 조회 REST API 컨트롤러 (비인증)
package com.codeprint.interfaces.api;

import com.codeprint.application.featured.FeaturedRepoService;
import com.codeprint.domain.featured.FeaturedRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/featured-repos")
@RequiredArgsConstructor
public class FeaturedRepoController {

    private final FeaturedRepoService featuredRepoService;

    // 오늘 선정된 공개레포 목록 조회 — 랜딩페이지 노출용
    @GetMapping
    public List<FeaturedRepoResponse> getFeaturedRepos() {
        return featuredRepoService.getCurrentFeatured().stream()
                .map(FeaturedRepoResponse::from)
                .toList();
    }

    record FeaturedRepoResponse(UUID projectId, String repoFullName, String language, Integer stars,
                                 String description, String ogImageUrl) {

        static FeaturedRepoResponse from(FeaturedRepo repo) {
            return new FeaturedRepoResponse(
                    repo.getProjectId(),
                    repo.getRepoFullName(),
                    repo.getLanguage(),
                    repo.getStars(),
                    repo.getDescription(),
                    "https://opengraph.githubassets.com/1/" + repo.getRepoFullName()
            );
        }
    }
}
