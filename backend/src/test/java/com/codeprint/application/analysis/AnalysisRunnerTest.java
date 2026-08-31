// AnalysisRunner 단위 테스트 — 동시성 슬롯 반납이 성공/실패 무관하게 항상 일어나는지 회귀 방지
// (2026-07-30 적대적 검증 — 슬롯 점유 구간이 "제출"이 아니라 "실제 작업 완료"까지여야 한다는 설계 검증)
package com.codeprint.application.analysis;

import com.codeprint.domain.analysis.AnalysisRepository;
import com.codeprint.domain.analysis.AnalysisResult;
import com.codeprint.domain.analysis.port.WarningDetectionPort;
import com.codeprint.domain.graph.Graph;
import com.codeprint.infrastructure.analysis.*;
import com.codeprint.infrastructure.config.AnalysisConcurrencyGuard;
import com.codeprint.infrastructure.github.GitHubApiClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalysisRunnerTest {

    @Mock private AnalysisRepository analysisRepository;
    @Mock private GitHubApiClient gitHubApiClient;
    @Mock private RepoCloner repoCloner;
    @Mock private SourceFileWalker sourceFileWalker;
    @Mock private CachedParsedFileLoader cachedParsedFileLoader;
    @Mock private GraphBuilder graphBuilder;
    @Mock private AnalysisConcurrencyGuard concurrencyGuard;
    @Mock private WarningDetectionPort warningDetectionPort;

    private AnalysisRunner runner() {
        return new AnalysisRunner(analysisRepository, gitHubApiClient, repoCloner, sourceFileWalker,
                cachedParsedFileLoader, graphBuilder, concurrencyGuard, warningDetectionPort);
    }

    @Test
    @DisplayName("분석이 정상 완료되면 동시성 슬롯을 반납한다")
    void 정상완료_슬롯반납() throws Exception {
        UUID analysisId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        AnalysisResult analysis = AnalysisResult.create(projectId, "main");
        when(analysisRepository.findById(analysisId)).thenReturn(Optional.of(analysis));
        Path repoDir = Path.of("/tmp/repo");
        when(repoCloner.clone("https://github.com/a/b", "main")).thenReturn(repoDir);
        when(sourceFileWalker.walk(repoDir, (String) null)).thenReturn(new WalkResult(List.of(), 0));
        when(cachedParsedFileLoader.load(any(), any(), any())).thenReturn(List.of());
        Graph graph = Graph.create(projectId, analysisId);
        when(graphBuilder.build(any(), any(), any(), anyInt())).thenReturn(graph);
        when(gitHubApiClient.fetchLatestCommitSha("https://github.com/a/b", "main", "tok")).thenReturn("sha1");

        runner().run(analysisId, projectId, "https://github.com/a/b", "main", "tok", null);

        verify(concurrencyGuard).release();
        verify(repoCloner).deleteDir(repoDir);
        verify(warningDetectionPort).detectWarnings(graph.getId());
    }

    @Test
    @DisplayName("분석 중 예외가 나도(클론 실패) 동시성 슬롯을 반납한다")
    void 예외발생_슬롯반납() throws Exception {
        UUID analysisId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        AnalysisResult analysis = AnalysisResult.create(projectId, "main");
        when(analysisRepository.findById(analysisId)).thenReturn(Optional.of(analysis));
        when(repoCloner.clone("https://github.com/a/b", "main")).thenThrow(new RuntimeException("clone 실패"));

        runner().run(analysisId, projectId, "https://github.com/a/b", "main", "tok", null);

        verify(concurrencyGuard).release();
    }
}
