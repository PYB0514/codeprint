// 오늘의 공개레포 일일 선정·분석 애플리케이션 서비스
package com.codeprint.application.featured;

import com.codeprint.domain.featured.FeaturedRepo;
import com.codeprint.domain.featured.FeaturedRepoRepository;
import com.codeprint.domain.featured.port.AnalysisTriggerPort;
import com.codeprint.domain.featured.port.ProjectProvisioningPort;
import com.codeprint.domain.featured.port.RepoMetadataPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class FeaturedRepoService {

    private static final int DAILY_COUNT = 5;

    private final FeaturedRepoRepository featuredRepoRepository;
    private final ProjectProvisioningPort projectProvisioningPort;
    private final AnalysisTriggerPort analysisTriggerPort;
    private final RepoMetadataPort repoMetadataPort;

    // 로테이션 후보 5개를 선정해 각각 갱신
    public void refreshDailyFeatured() {
        featuredRepoRepository.findRotationCandidates(DAILY_COUNT).forEach(this::featureOne);
    }

    // 레포 하나를 선정 처리 — (최초라면 프로젝트 생성 후) 분석 트리거 + 메타데이터 갱신
    private void featureOne(FeaturedRepo repo) {
        UUID projectId = resolveProjectId(repo);
        analysisTriggerPort.triggerAnalysis(projectId, repo.toGithubRepoUrl());
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
}
