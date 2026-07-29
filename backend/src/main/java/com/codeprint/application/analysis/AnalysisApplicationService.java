// 코드 분석 시작 및 조회 애플리케이션 서비스
package com.codeprint.application.analysis;

import com.codeprint.domain.analysis.AnalysisRepository;
import com.codeprint.domain.analysis.AnalysisResult;
import com.codeprint.domain.analysis.AnalysisStatus;
import com.codeprint.infrastructure.config.AnalysisConcurrencyGuard;
import com.codeprint.infrastructure.github.GitHubApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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

    // 이 크기(KB)를 넘는 레포는 클론/아카이브 다운로드 자체를 거부 — 대형 레포 하나가 8-슬롯 동시성 캡을
    // 클론 타임아웃(120초)까지 점유하는 DoS를 막는다(GitHub API "size"는 근사치라 완전한 보장은 아님)
    private static final long MAX_REPO_SIZE_KB = 1_048_576; // 1GB

    private final AnalysisRepository analysisRepository;
    private final AnalysisRunner analysisRunner;
    private final GitHubApiClient gitHubApiClient;
    private final AnalysisConcurrencyGuard concurrencyGuard;

    // 분석 레코드를 생성하고 비동기 분석을 시작 (URL·토큰은 컨트롤러에서 전달) — 직전 분석이 같은 커밋이면 스킵(FeaturedRepo 레버①과 동일 원칙)
    public AnalysisResult startAnalysis(UUID projectId, String branch, String githubRepoUrl, String githubAccessToken) {
        return startAnalysis(projectId, branch, githubRepoUrl, githubAccessToken, null);
    }

    // ref(특정 커밋 SHA)를 지정해 그 커밋 상태로 재분석 — 사용자가 명시적으로 요청한 것이므로 "동일 커밋 스킵"
    // 최적화를 적용하지 않고 항상 실행한다.
    public AnalysisResult startAnalysis(UUID projectId, String branch, String githubRepoUrl, String githubAccessToken, String ref) {
        // 레포 크기 조회(외부 API 호출)보다 먼저 확인 — 포화 상태면 불필요한 GitHub API 호출 자체를 생략
        if (concurrencyGuard.isAtCapacity()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "현재 분석 요청이 많아 처리할 수 없습니다. 잠시 후 다시 시도해주세요.");
        }

        long sizeKb = fetchRepoSizeKbSafely(githubRepoUrl, githubAccessToken);
        if (sizeKb > MAX_REPO_SIZE_KB) {
            throw new IllegalArgumentException("레포 크기가 너무 큽니다(" + sizeKb + "KB, 상한 " + MAX_REPO_SIZE_KB + "KB) — 분석을 시작할 수 없습니다.");
        }

        if (ref == null) {
            Optional<AnalysisResult> lastAnalysis = analysisRepository.findLatestByProjectIdAndBranch(projectId, branch);
            if (lastAnalysis.isPresent() && lastAnalysis.get().getStatus() == AnalysisStatus.DONE) {
                String latestSha = fetchLatestShaSafely(githubRepoUrl, branch, githubAccessToken);
                if (latestSha != null && latestSha.equals(lastAnalysis.get().getLastCommitSha())) {
                    log.info("커밋 변경 없음, 재분석 스킵: projectId={}, branch={}, sha={}", projectId, branch, latestSha);
                    return lastAnalysis.get();
                }
            }
        }

        AnalysisResult analysis = AnalysisResult.create(projectId, branch);
        analysisRepository.save(analysis);

        // URL을 미리 추출해서 넘김 — 트랜잭션 커밋 전 비동기 스레드가 DB 조회 시 못 찾는 문제 방지
        analysisRunner.run(analysis.getId(), projectId, githubRepoUrl, branch, githubAccessToken, ref);
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

    // 레포 크기 조회 실패 시 0 반환(상한 검사 통과) — API 장애로 정상 분석까지 막지 않도록 fail-open
    private long fetchRepoSizeKbSafely(String githubRepoUrl, String githubAccessToken) {
        try {
            return gitHubApiClient.fetchRepoSizeKb(githubRepoUrl, githubAccessToken);
        } catch (Exception e) {
            log.warn("레포 크기 조회 실패 (무시, 상한 검사 통과 처리): {}", githubRepoUrl, e);
            return 0L;
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
