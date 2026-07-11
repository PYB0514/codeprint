// FeaturedRepoService 일일 선정 분기 단위 테스트 — 최초 노출(프로젝트 생성) vs 재노출(기존 프로젝트 재사용) +
// 통합 게시글 최초 생성 vs 재사용 + 그래프 미완료 레포 제외 분기 회귀 방지
package com.codeprint.application.featured;

import com.codeprint.domain.featured.FeaturedPostRepository;
import com.codeprint.domain.featured.FeaturedRepo;
import com.codeprint.domain.featured.FeaturedRepoRepository;
import com.codeprint.domain.featured.port.AnalysisTriggerPort;
import com.codeprint.domain.featured.port.PostPublishingPort;
import com.codeprint.domain.featured.port.ProjectProvisioningPort;
import com.codeprint.domain.featured.port.RepoMetadataPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeaturedRepoServiceTest {

    @Mock private FeaturedRepoRepository featuredRepoRepository;
    @Mock private ProjectProvisioningPort projectProvisioningPort;
    @Mock private AnalysisTriggerPort analysisTriggerPort;
    @Mock private RepoMetadataPort repoMetadataPort;
    @Mock private PostPublishingPort postPublishingPort;
    @Mock private FeaturedPostRepository featuredPostRepository;

    private FeaturedRepoService service;

    @BeforeEach
    void setUp() {
        service = new FeaturedRepoService(featuredRepoRepository, projectProvisioningPort, analysisTriggerPort,
                repoMetadataPort, postPublishingPort, featuredPostRepository);
    }

    @Test
    @DisplayName("최초 노출인 레포(projectId=null)는 프로젝트를 새로 생성한 뒤 분석 트리거")
    void refreshDailyFeatured_createsProjectForFirstTimeRepo() throws Exception {
        FeaturedRepo repo = newFeaturedRepo("torvalds/linux", null);
        UUID newProjectId = UUID.randomUUID();
        when(featuredRepoRepository.findRotationCandidates(5)).thenReturn(List.of(repo));
        when(projectProvisioningPort.createSystemProject(eq("https://github.com/torvalds/linux"), eq("torvalds/linux")))
                .thenReturn(newProjectId);
        when(repoMetadataPort.fetch("torvalds/linux")).thenReturn(new RepoMetadataPort.RepoMetadata(180000, "Linux kernel"));

        service.refreshDailyFeatured();

        verify(projectProvisioningPort).createSystemProject("https://github.com/torvalds/linux", "torvalds/linux");
        verify(analysisTriggerPort).triggerAnalysis(newProjectId, "https://github.com/torvalds/linux");
        assertThat(repo.getProjectId()).isEqualTo(newProjectId);
        assertThat(repo.getStars()).isEqualTo(180000);
        verify(featuredRepoRepository).save(repo);
    }

    @Test
    @DisplayName("이미 프로젝트가 있는 레포는 재생성 없이 기존 projectId로 분석만 재트리거")
    void refreshDailyFeatured_reusesExistingProject() throws Exception {
        UUID existingProjectId = UUID.randomUUID();
        FeaturedRepo repo = newFeaturedRepo("expressjs/express", existingProjectId);
        when(featuredRepoRepository.findRotationCandidates(5)).thenReturn(List.of(repo));
        when(repoMetadataPort.fetch("expressjs/express")).thenReturn(new RepoMetadataPort.RepoMetadata(65000, "Fast web framework"));

        service.refreshDailyFeatured();

        verify(projectProvisioningPort, never()).createSystemProject(any(), any());
        verify(analysisTriggerPort).triggerAnalysis(existingProjectId, "https://github.com/expressjs/express");
        assertThat(repo.getProjectId()).isEqualTo(existingProjectId);
    }

    @Test
    @DisplayName("getCurrentFeatured — 레포지토리 조회 결과 그대로 반환")
    void getCurrentFeatured_delegatesToRepository() throws Exception {
        FeaturedRepo repo = newFeaturedRepo("gin-gonic/gin", UUID.randomUUID());
        when(featuredRepoRepository.findMostRecentlyFeatured(5)).thenReturn(List.of(repo));

        assertThat(service.getCurrentFeatured()).containsExactly(repo);
    }

    @Test
    @DisplayName("통합 게시글이 없으면(최초 실행) 새로 생성하고 postId를 저장한다")
    void refreshDailyFeatured_createsPostWhenNoneExists() throws Exception {
        UUID projectId = UUID.randomUUID();
        FeaturedRepo repo = newFeaturedRepo("torvalds/linux", projectId);
        UUID newPostId = UUID.randomUUID();
        when(featuredRepoRepository.findRotationCandidates(5)).thenReturn(List.of(repo));
        when(featuredRepoRepository.findMostRecentlyFeatured(5)).thenReturn(List.of(repo));
        when(repoMetadataPort.fetch("torvalds/linux")).thenReturn(new RepoMetadataPort.RepoMetadata(180000, "Linux kernel"));
        when(featuredPostRepository.findPostId()).thenReturn(Optional.empty());
        when(postPublishingPort.createPost(any(), any())).thenReturn(newPostId);
        when(postPublishingPort.captureSnapshot(projectId)).thenReturn(Optional.empty());

        service.refreshDailyFeatured();

        verify(postPublishingPort).createPost(any(), any());
        verify(featuredPostRepository).savePostId(newPostId);
        verify(postPublishingPort).replaceSnapshots(newPostId, List.of());
    }

    @Test
    @DisplayName("통합 게시글이 이미 있으면 재생성 없이 기존 postId로 스냅샷만 교체한다")
    void refreshDailyFeatured_reusesExistingPost() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID existingPostId = UUID.randomUUID();
        FeaturedRepo repo = newFeaturedRepo("expressjs/express", projectId);
        when(featuredRepoRepository.findRotationCandidates(5)).thenReturn(List.of(repo));
        when(featuredRepoRepository.findMostRecentlyFeatured(5)).thenReturn(List.of(repo));
        when(repoMetadataPort.fetch("expressjs/express")).thenReturn(new RepoMetadataPort.RepoMetadata(65000, "Fast web framework"));
        when(featuredPostRepository.findPostId()).thenReturn(Optional.of(existingPostId));
        when(postPublishingPort.captureSnapshot(projectId)).thenReturn(Optional.empty());

        service.refreshDailyFeatured();

        verify(postPublishingPort, never()).createPost(any(), any());
        verify(featuredPostRepository, never()).savePostId(any());
        verify(postPublishingPort).replaceSnapshots(existingPostId, List.of());
    }

    @Test
    @DisplayName("분석이 아직 안 끝나 그래프가 없는 레포는 스냅샷 목록에서 제외된다")
    void refreshDailyFeatured_skipsReposWithoutGraphYet() throws Exception {
        UUID readyProjectId = UUID.randomUUID();
        UUID notReadyProjectId = UUID.randomUUID();
        UUID graphId = UUID.randomUUID();
        FeaturedRepo ready = newFeaturedRepo("psf/requests", readyProjectId);
        FeaturedRepo notReady = newFeaturedRepo("new/repo", notReadyProjectId);
        PostPublishingPort.SnapshotToPublish readySnapshot =
                new PostPublishingPort.SnapshotToPublish(readyProjectId, graphId, Map.of());
        when(featuredRepoRepository.findRotationCandidates(5)).thenReturn(List.of(ready, notReady));
        when(featuredRepoRepository.findMostRecentlyFeatured(5)).thenReturn(List.of(ready, notReady));
        when(repoMetadataPort.fetch(any())).thenReturn(new RepoMetadataPort.RepoMetadata(1000, "desc"));
        when(featuredPostRepository.findPostId()).thenReturn(Optional.of(UUID.randomUUID()));
        when(postPublishingPort.captureSnapshot(readyProjectId)).thenReturn(Optional.of(readySnapshot));
        when(postPublishingPort.captureSnapshot(notReadyProjectId)).thenReturn(Optional.empty());

        service.refreshDailyFeatured();

        verify(postPublishingPort).replaceSnapshots(any(), eq(List.of(readySnapshot)));
    }

    // protected 생성자 우회 — FeaturedRepo는 JPA 엔티티라 테스트용 팩토리가 없음
    private FeaturedRepo newFeaturedRepo(String repoFullName, UUID projectId) throws Exception {
        var constructor = FeaturedRepo.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        FeaturedRepo repo = constructor.newInstance();
        ReflectionTestUtils.setField(repo, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(repo, "repoFullName", repoFullName);
        ReflectionTestUtils.setField(repo, "language", "Java");
        ReflectionTestUtils.setField(repo, "projectId", projectId);
        ReflectionTestUtils.setField(repo, "createdAt", Instant.now());
        return repo;
    }
}
