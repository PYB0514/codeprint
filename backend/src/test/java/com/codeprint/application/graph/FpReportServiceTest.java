// FpReportService 단위 테스트 — 오탐 신고 멱등성·조회·재현 페이로드(코드 스니펫 최선노력 확보) 회귀 방지
package com.codeprint.application.graph;

import com.codeprint.domain.graph.FpReport;
import com.codeprint.domain.graph.FpReportRepository;
import com.codeprint.domain.graph.Graph;
import com.codeprint.domain.graph.GraphRepository;
import com.codeprint.domain.graph.Node;
import com.codeprint.domain.graph.NodeType;
import com.codeprint.domain.graph.port.AnalysisReadPort;
import com.codeprint.domain.graph.port.ProjectAccessPort;
import com.codeprint.infrastructure.github.GitHubApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FpReportServiceTest {

    @Mock private FpReportRepository repository;
    @Mock private GraphRepository graphRepository;
    @Mock private AnalysisReadPort analysisReadPort;
    @Mock private ProjectAccessPort projectAccessPort;
    @Mock private GitHubApiClient gitHubApiClient;

    private FpReportService service;

    @BeforeEach
    void setUp() {
        service = new FpReportService(repository, graphRepository, analysisReadPort, projectAccessPort, gitHubApiClient);
    }

    @Test
    @DisplayName("reportFalsePositive — 아직 신고하지 않았으면 저장")
    void report_notExists_saves() {
        UUID projectId = UUID.randomUUID();
        UUID reporterId = UUID.randomUUID();
        when(repository.existsByProjectIdAndFingerprintAndReporterId(projectId, "fp1", reporterId)).thenReturn(false);

        service.reportFalsePositive(projectId, null, "fp1", "CROSS_DOMAIN_CALL", reporterId, "실제 사용 중인 코드입니다",
                "message", "src/Foo.java", 10, 4, 20);

        verify(repository).save(any(FpReport.class));
    }

    @Test
    @DisplayName("reportFalsePositive — 동일 사용자가 이미 신고했으면 저장하지 않음(멱등)")
    void report_alreadyExists_noSave() {
        UUID projectId = UUID.randomUUID();
        UUID reporterId = UUID.randomUUID();
        when(repository.existsByProjectIdAndFingerprintAndReporterId(projectId, "fp1", reporterId)).thenReturn(true);

        service.reportFalsePositive(projectId, null, "fp1", "CROSS_DOMAIN_CALL", reporterId, null,
                null, null, null, null, null);

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("reportFalsePositive — graphId 없으면 스니펫 확보 시도 없이 구조적 필드만 저장")
    void report_noGraphId_skipsSnippetCapture() {
        UUID projectId = UUID.randomUUID();
        UUID reporterId = UUID.randomUUID();
        when(repository.existsByProjectIdAndFingerprintAndReporterId(any(), any(), any())).thenReturn(false);

        service.reportFalsePositive(projectId, null, "fp1", "DEAD_CODE", reporterId, null,
                "message", "src/Foo.java", 10, null, null);

        verifyNoInteractions(graphRepository, analysisReadPort, projectAccessPort, gitHubApiClient);
        verify(repository).save(any(FpReport.class));
    }

    @Test
    @DisplayName("reportFalsePositive — 커밋 SHA·레포 URL 모두 확보되면 GitHub에서 스니펫을 최선노력으로 가져와 저장")
    void report_withGraphAndPublicRepo_capturesSnippet() {
        UUID projectId = UUID.randomUUID();
        UUID reporterId = UUID.randomUUID();
        UUID graphId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();
        Graph graph = Graph.create(projectId, analysisId);
        when(repository.existsByProjectIdAndFingerprintAndReporterId(any(), any(), any())).thenReturn(false);
        when(graphRepository.findById(graphId)).thenReturn(Optional.of(graph));
        when(graphRepository.findNodesByGraphId(graphId))
                .thenReturn(List.of(Node.create(graphId, NodeType.FILE, "Foo.java", "src/Foo.java", "java")));
        when(analysisReadPort.findCommitSha(analysisId)).thenReturn(Optional.of("abc123"));
        when(projectAccessPort.findGithubRepoUrl(projectId)).thenReturn(Optional.of("https://github.com/o/r"));
        when(gitHubApiClient.fetchFileContent("https://github.com/o/r", "src/Foo.java", "abc123"))
                .thenReturn("line1\nline2\nline3\nline4\nline5\nline6\nline7\nline8\nline9\nline10\nline11");

        service.reportFalsePositive(projectId, graphId, "fp1", "DEAD_CODE", reporterId, null,
                "message", "src/Foo.java", 6, null, null);

        verify(repository).save(argThat(r -> r.getCodeSnippet() != null && r.getCodeSnippet().contains("line6")));
    }

    @Test
    @DisplayName("reportFalsePositive — filePath가 그래프가 실제로 분석한 노드 경로와 다르면 스니펫 확보 안 함(임의 경로 조회 차단)")
    void report_filePathNotInGraph_skipsSnippetCapture() {
        UUID projectId = UUID.randomUUID();
        UUID reporterId = UUID.randomUUID();
        UUID graphId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();
        Graph graph = Graph.create(projectId, analysisId);
        when(repository.existsByProjectIdAndFingerprintAndReporterId(any(), any(), any())).thenReturn(false);
        when(graphRepository.findById(graphId)).thenReturn(Optional.of(graph));
        when(graphRepository.findNodesByGraphId(graphId))
                .thenReturn(List.of(Node.create(graphId, NodeType.FILE, "Foo.java", "src/Foo.java", "java")));

        service.reportFalsePositive(projectId, graphId, "fp1", "DEAD_CODE", reporterId, null,
                "message", "../../../../etc/passwd", 6, null, null);

        verifyNoInteractions(analysisReadPort, projectAccessPort, gitHubApiClient);
        verify(repository).save(argThat(r -> r.getCodeSnippet() == null));
    }

    @Test
    @DisplayName("reportFalsePositive — graphId가 요청한 projectId 소유가 아니면 스니펫 확보 안 함(교차 프로젝트 차단)")
    void report_graphBelongsToOtherProject_skipsSnippetCapture() {
        UUID projectId = UUID.randomUUID();
        UUID otherProjectId = UUID.randomUUID();
        UUID reporterId = UUID.randomUUID();
        UUID graphId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();
        Graph graph = Graph.create(otherProjectId, analysisId);
        when(repository.existsByProjectIdAndFingerprintAndReporterId(any(), any(), any())).thenReturn(false);
        when(graphRepository.findById(graphId)).thenReturn(Optional.of(graph));

        service.reportFalsePositive(projectId, graphId, "fp1", "DEAD_CODE", reporterId, null,
                "message", "src/Foo.java", 6, null, null);

        verifyNoInteractions(analysisReadPort, projectAccessPort, gitHubApiClient);
        verify(repository).save(argThat(r -> r.getCodeSnippet() == null));
    }

    @Test
    @DisplayName("reportFalsePositive — 커밋 SHA를 못 구하면(레포 미연동 등) 스니펫 없이도 신고는 성공")
    void report_noCommitSha_stillSavesWithoutSnippet() {
        UUID projectId = UUID.randomUUID();
        UUID reporterId = UUID.randomUUID();
        UUID graphId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();
        Graph graph = Graph.create(projectId, analysisId);
        when(repository.existsByProjectIdAndFingerprintAndReporterId(any(), any(), any())).thenReturn(false);
        when(graphRepository.findById(graphId)).thenReturn(Optional.of(graph));
        when(graphRepository.findNodesByGraphId(graphId))
                .thenReturn(List.of(Node.create(graphId, NodeType.FILE, "Foo.java", "src/Foo.java", "java")));
        when(analysisReadPort.findCommitSha(analysisId)).thenReturn(Optional.empty());

        service.reportFalsePositive(projectId, graphId, "fp1", "DEAD_CODE", reporterId, null,
                "message", "src/Foo.java", 6, null, null);

        verifyNoInteractions(gitHubApiClient);
        verify(repository).save(argThat(r -> r.getCodeSnippet() == null));
    }

    @Test
    @DisplayName("getReportedFingerprintsByUser — 리포지토리의 fingerprint 집합 반환")
    void getReported_returnsFromRepo() {
        UUID projectId = UUID.randomUUID();
        UUID reporterId = UUID.randomUUID();
        when(repository.findFingerprintsByProjectIdAndReporterId(projectId, reporterId)).thenReturn(Set.of("fp1", "fp2"));

        Set<String> result = service.getReportedFingerprintsByUser(projectId, reporterId);

        assertThat(result).containsExactlyInAnyOrder("fp1", "fp2");
    }
}
