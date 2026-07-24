// 코드 분석 시작 및 조회 애플리케이션 서비스
package com.codeprint.application.analysis;

import com.codeprint.domain.analysis.AnalysisRepository;
import com.codeprint.domain.analysis.AnalysisResult;
import com.codeprint.domain.analysis.AnalysisStatus;
import com.codeprint.infrastructure.github.GitHubApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AnalysisApplicationService {

    private final AnalysisRepository analysisRepository;
    private final AnalysisRunner analysisRunner;
    private final GitHubApiClient gitHubApiClient;

    // 분석 레코드를 생성하고 비동기 분석을 시작 (URL·토큰은 컨트롤러에서 전달) — 직전 분석이 같은 커밋이면 스킵(FeaturedRepo 레버①과 동일 원칙)
    public AnalysisResult startAnalysis(UUID projectId, String branch, String githubRepoUrl, String githubAccessToken) {
        Optional<AnalysisResult> lastAnalysis = analysisRepository.findLatestByProjectIdAndBranch(projectId, branch);
        if (lastAnalysis.isPresent() && lastAnalysis.get().getStatus() == AnalysisStatus.DONE) {
            String latestSha = fetchLatestShaSafely(githubRepoUrl, branch, githubAccessToken);
            if (latestSha != null && latestSha.equals(lastAnalysis.get().getLastCommitSha())) {
                log.info("커밋 변경 없음, 재분석 스킵: projectId={}, branch={}, sha={}", projectId, branch, latestSha);
                return lastAnalysis.get();
            }
        }

        AnalysisResult analysis = AnalysisResult.create(projectId, branch);
        analysisRepository.save(analysis);

        // URL을 미리 추출해서 넘김 — 트랜잭션 커밋 전 비동기 스레드가 DB 조회 시 못 찾는 문제 방지
        analysisRunner.run(analysis.getId(), projectId, githubRepoUrl, branch, githubAccessToken);
        return analysis;
    }

    // GitHub 커밋 SHA 조회 실패 시 null 반환 — 스킵 판정만 안전하게 포기, 분석 자체는 정상 진행
    private String fetchLatestShaSafely(String githubRepoUrl, String branch, String githubAccessToken) {
        try {
            return gitHubApiClient.fetchLatestCommitSha(githubRepoUrl, branch, githubAccessToken);
        } catch (Exception e) {
            log.warn("커밋 SHA 조회 실패 (무시, 안전하게 재분석): branch={}", branch, e);
            return null;
        }
    }

    // 최신 분석 결과 조회
    @Transactional(readOnly = true)
    public Optional<AnalysisResult> getLatestAnalysis(UUID projectId) {
        return analysisRepository.findLatestByProjectId(projectId);
    }

    // 특정 브랜치의 최신 분석 결과 조회
    @Transactional(readOnly = true)
    public Optional<AnalysisResult> getLatestAnalysisByBranch(UUID projectId, String branch) {
        return analysisRepository.findLatestByProjectIdAndBranch(projectId, branch);
    }

    // 분석 ID로 분석 결과를 조회
    @Transactional(readOnly = true)
    public AnalysisResult getAnalysis(UUID analysisId) {
        return analysisRepository.findById(analysisId)
                .orElseThrow(() -> new IllegalArgumentException("Analysis not found: " + analysisId));
    }

    // 여러 analysisId의 브랜치를 한 번에 조회 — N+1 방지용 배치 조회
    @Transactional(readOnly = true)
    public Map<UUID, String> getBranchMap(List<UUID> analysisIds) {
        return analysisRepository.findAllById(analysisIds).stream()
                .collect(Collectors.toMap(
                        AnalysisResult::getId,
                        a -> a.getBranch() != null ? a.getBranch() : "default"
                ));
    }
}
