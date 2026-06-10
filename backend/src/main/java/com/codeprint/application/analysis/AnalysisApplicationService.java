// 코드 분석 시작 및 조회 애플리케이션 서비스
package com.codeprint.application.analysis;

import com.codeprint.domain.analysis.AnalysisRepository;
import com.codeprint.domain.analysis.AnalysisResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class AnalysisApplicationService {

    private final AnalysisRepository analysisRepository;
    private final AnalysisRunner analysisRunner;

    // 분석 레코드를 생성하고 비동기 분석을 시작 (URL·토큰은 컨트롤러에서 전달)
    public AnalysisResult startAnalysis(UUID projectId, String branch, String githubRepoUrl, String githubAccessToken) {
        AnalysisResult analysis = AnalysisResult.create(projectId, branch);
        analysisRepository.save(analysis);

        // URL을 미리 추출해서 넘김 — 트랜잭션 커밋 전 비동기 스레드가 DB 조회 시 못 찾는 문제 방지
        analysisRunner.run(analysis.getId(), projectId, githubRepoUrl, branch, githubAccessToken);
        return analysis;
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
