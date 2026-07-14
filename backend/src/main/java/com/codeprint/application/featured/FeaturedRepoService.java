// 오늘의 공개레포 일일 선정·분석 애플리케이션 서비스
package com.codeprint.application.featured;

import com.codeprint.domain.featured.FeaturedPostRepository;
import com.codeprint.domain.featured.FeaturedRepo;
import com.codeprint.domain.featured.FeaturedRepoRepository;
import com.codeprint.domain.featured.port.AnalysisTriggerPort;
import com.codeprint.domain.featured.port.CommitShaPort;
import com.codeprint.domain.featured.port.PostPublishingPort;
import com.codeprint.domain.featured.port.ProjectProvisioningPort;
import com.codeprint.domain.featured.port.RepoMetadataPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class FeaturedRepoService {

    private static final int DAILY_COUNT = 5;
    private static final String POST_TITLE = "오늘의 공개레포";
    private static final String POST_CONTENT = "매일 자동으로 선정되는 인기 오픈소스 레포 5개를 Codeprint로 분석한 그래프입니다. 매일 06:00(KST) 갱신됩니다.";

    private final FeaturedRepoRepository featuredRepoRepository;
    private final ProjectProvisioningPort projectProvisioningPort;
    private final AnalysisTriggerPort analysisTriggerPort;
    private final RepoMetadataPort repoMetadataPort;
    private final CommitShaPort commitShaPort;
    private final PostPublishingPort postPublishingPort;
    private final FeaturedPostRepository featuredPostRepository;

    // 로테이션 후보 5개를 선정해 각각 갱신 후, 통합 게시글의 스냅샷을 최신 상태로 재발행
    public void refreshDailyFeatured() {
        featuredRepoRepository.findRotationCandidates(DAILY_COUNT).forEach(this::featureOne);
        republishPost();
    }

    // 통합 게시글이 없으면 생성(최초 1회), 있으면 재사용 — 현재 노출 중인 5개의 그래프 스냅샷으로 교체
    private void republishPost() {
        UUID postId = featuredPostRepository.findPostId().orElseGet(this::createPost);

        List<PostPublishingPort.SnapshotToPublish> snapshots = getCurrentFeatured().stream()
                .map(repo -> postPublishingPort.captureSnapshot(repo.getProjectId()))
                .flatMap(Optional::stream)
                .toList();

        postPublishingPort.replaceSnapshots(postId, snapshots);
    }

    // 통합 게시글을 최초 생성하고 postId를 저장
    private UUID createPost() {
        UUID postId = postPublishingPort.createPost(POST_TITLE, POST_CONTENT);
        featuredPostRepository.savePostId(postId);
        return postId;
    }

    // 레포 하나를 선정 처리 — (최초라면 프로젝트 생성 후) 커밋이 바뀐 경우에만 분석 트리거 + 메타데이터 갱신
    private void featureOne(FeaturedRepo repo) {
        boolean isFirstFeature = repo.getProjectId() == null;
        UUID projectId = resolveProjectId(repo);
        String latestSha = commitShaPort.fetchLatestCommitSha(repo.getRepoFullName()).orElse(null);

        if (!isFirstFeature && repo.isCommitUnchanged(latestSha)) {
            log.info("커밋 변경 없음, 재분석 스킵: {} ({})", repo.getRepoFullName(), latestSha);
        } else {
            analysisTriggerPort.triggerAnalysis(projectId, repo.toGithubRepoUrl());
            repo.markCommitAnalyzed(latestSha);
        }

        persistMetadata(repo, projectId);
    }

    // 최초 노출이면 시스템 프로젝트를 새로 생성, 아니면 기존 projectId 재사용
    private UUID resolveProjectId(FeaturedRepo repo) {
        UUID projectId = repo.getProjectId();
        if (projectId != null) {
            return projectId;
        }
        return projectProvisioningPort.createSystemProject(repo.toGithubRepoUrl(), repo.getRepoFullName());
    }

    // star·description을 갱신하고 노출 시각과 함께 저장
    private void persistMetadata(FeaturedRepo repo, UUID projectId) {
        RepoMetadataPort.RepoMetadata metadata = repoMetadataPort.fetch(repo.getRepoFullName());
        repo.markFeatured(projectId, metadata.stars(), metadata.description(), Instant.now());
        featuredRepoRepository.save(repo);
    }

    // 랜딩페이지 노출용 — 분석까지 완료된 최근 노출 목록 조회
    @Transactional(readOnly = true)
    public List<FeaturedRepo> getCurrentFeatured() {
        return featuredRepoRepository.findMostRecentlyFeatured(DAILY_COUNT);
    }

    // 랜딩페이지 노출용 — 각 레포에 통합 게시글 postId·스냅샷 position을 붙여 반환(스냅샷 없으면 position=null, 카드 딥링크 불가)
    @Transactional(readOnly = true)
    public List<FeaturedDisplayItem> getCurrentFeaturedForDisplay() {
        UUID postId = featuredPostRepository.findPostId().orElse(null);
        Map<UUID, Integer> positions = postId == null ? Map.of() : postPublishingPort.getSnapshotPositions(postId);
        return getCurrentFeatured().stream()
                .map(repo -> new FeaturedDisplayItem(repo, postId, positions.get(repo.getProjectId())))
                .toList();
    }

    // 랜딩페이지 카드 하나 — 레포 정보 + 통합 게시글 딥링크(postId+position, 스냅샷 미완료면 position null)
    public record FeaturedDisplayItem(FeaturedRepo repo, UUID postId, Integer position) {}
}
