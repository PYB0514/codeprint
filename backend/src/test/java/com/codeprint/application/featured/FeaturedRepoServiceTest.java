// FeaturedRepoService 일일 선정 분기 단위 테스트 — 최초 노출(프로젝트 생성) vs 재노출(기존 프로젝트 재사용) 분기 회귀 방지
package com.codeprint.application.featured;

import com.codeprint.domain.featured.FeaturedRepo;
import com.codeprint.domain.featured.FeaturedRepoRepository;
import com.codeprint.domain.featured.port.AnalysisTriggerPort;
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

    private FeaturedRepoService service;

    @BeforeEach
    void setUp() {
        service = new FeaturedRepoService(featuredRepoRepository, projectProvisioningPort, analysisTriggerPort, repoMetadataPort);
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
