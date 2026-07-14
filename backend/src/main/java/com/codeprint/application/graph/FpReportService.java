// 오탐 신고 애플리케이션 서비스
package com.codeprint.application.graph;

import com.codeprint.domain.graph.FpReport;
import com.codeprint.domain.graph.FpReportRepository;
import com.codeprint.domain.graph.Graph;
import com.codeprint.domain.graph.GraphRepository;
import com.codeprint.domain.graph.port.AnalysisReadPort;
import com.codeprint.domain.graph.port.ProjectAccessPort;
import com.codeprint.infrastructure.github.GitHubApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FpReportService {

    // 스니펫에 포함할 대상 줄 앞뒤 여유 줄 수
    private static final int SNIPPET_CONTEXT_LINES = 5;

    private final FpReportRepository repository;
    private final GraphRepository graphRepository;
    private final AnalysisReadPort analysisReadPort;
    private final ProjectAccessPort projectAccessPort;
    private final GitHubApiClient gitHubApiClient;

    // 오탐 신고 — 동일 사용자가 이미 신고했으면 무시(멱등). graphId·filePath·line이 있으면 최선노력으로 재현용 코드 스니펫도 함께 저장.
    @Transactional
    public void reportFalsePositive(UUID projectId, UUID graphId, String fingerprint, String warningType, UUID reporterId,
                                     String reason, String message, String filePath, Integer line, Integer col, Integer endCol) {
        if (repository.existsByProjectIdAndFingerprintAndReporterId(projectId, fingerprint, reporterId)) {
            return;
        }
        String snippet = captureSnippet(projectId, graphId, filePath, line);
        repository.save(FpReport.create(projectId, fingerprint, warningType, reporterId, reason,
                message, filePath, line, col, endCol, snippet));
    }

    // GitHub 공개 레포 + 신고 시점 그래프가 분석한 정확한 커밋 SHA를 알 때만 코드 스니펫 확보 — 어느 단계든 실패하면 null(신고 자체는 항상 성공)
    private String captureSnippet(UUID projectId, UUID graphId, String filePath, Integer line) {
        if (graphId == null || filePath == null || filePath.isBlank() || line == null) return null;
        try {
            UUID analysisId = graphRepository.findById(graphId).map(Graph::getAnalysisId).orElse(null);
            if (analysisId == null) return null;
            String sha = analysisReadPort.findCommitSha(analysisId).orElse(null);
            if (sha == null) return null;
            String repoUrl = projectAccessPort.findGithubRepoUrl(projectId).orElse(null);
            if (repoUrl == null) return null;
            String content = gitHubApiClient.fetchFileContent(repoUrl, filePath, sha);
            return GitHubApiClient.extractSnippet(content, line, SNIPPET_CONTEXT_LINES);
        } catch (Exception e) {
            log.warn("오탐 신고 코드 스니펫 확보 실패(최선노력, 무시): projectId={} graphId={}", projectId, graphId);
            return null;
        }
    }

    // 사용자가 신고한 fingerprint 집합 조회 — 버튼 상태 표시용
    @Transactional(readOnly = true)
    public Set<String> getReportedFingerprintsByUser(UUID projectId, UUID reporterId) {
        return repository.findFingerprintsByProjectIdAndReporterId(projectId, reporterId);
    }
}
