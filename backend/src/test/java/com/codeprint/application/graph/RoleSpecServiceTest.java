// RoleSpecService 단위 테스트 — BYOK 유무·스코프 필터링·최선노력 실패 처리 회귀 방지
package com.codeprint.application.graph;

import com.codeprint.domain.graph.port.AiKeyPort;
import com.codeprint.domain.graph.Edge;
import com.codeprint.domain.graph.EdgeType;
import com.codeprint.domain.graph.Graph;
import com.codeprint.domain.graph.GraphRepository;
import com.codeprint.domain.graph.Node;
import com.codeprint.domain.graph.NodeType;
import com.codeprint.domain.graph.port.AnalysisReadPort;
import com.codeprint.domain.graph.port.ProjectAccessPort;
import com.codeprint.shared.ai.AiProvider;
import com.codeprint.infrastructure.ai.AiService;
import com.codeprint.infrastructure.github.GitHubApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoleSpecServiceTest {

    @Mock private AiKeyPort aiKeyPort;
    @Mock private GraphQueryService graphQueryService;
    @Mock private GraphRepository graphRepository;
    @Mock private AnalysisReadPort analysisReadPort;
    @Mock private ProjectAccessPort projectAccessPort;
    @Mock private GitHubApiClient gitHubApiClient;
    @Mock private AiService aiService;

    private RoleSpecService service;

    private final UUID projectId = UUID.randomUUID();
    private final UUID graphId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID analysisId = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        lenient().when(aiService.provider()).thenReturn(AiProvider.ANTHROPIC);
        service = new RoleSpecService(aiKeyPort, graphQueryService, graphRepository,
                analysisReadPort, projectAccessPort, gitHubApiClient, List.of(aiService));
        // @PostConstruct는 Mockito가 호출하지 않아 리플렉션으로 직접 실행
        Method init = RoleSpecService.class.getDeclaredMethod("init");
        init.setAccessible(true);
        init.invoke(service);
    }

    @Test
    @DisplayName("provider가 null이면 빈 문자열")
    void nullProvider_returnsEmpty() {
        String result = service.generateSection(projectId, graphId, userId, null);

        assertThat(result).isEmpty();
        verifyNoInteractions(aiKeyPort);
    }

    @Test
    @DisplayName("BYOK 키 미등록이면 빈 문자열")
    void noKey_returnsEmpty() {
        when(aiKeyPort.findPlainKey(userId, AiProvider.ANTHROPIC)).thenReturn(Optional.empty());

        String result = service.generateSection(projectId, graphId, userId, AiProvider.ANTHROPIC);

        assertThat(result).isEmpty();
        verifyNoInteractions(graphRepository);
    }

    @Test
    @DisplayName("그래프가 다른 프로젝트 소속이면 빈 문자열")
    void graphBelongsToOtherProject_returnsEmpty() {
        when(aiKeyPort.findPlainKey(userId, AiProvider.ANTHROPIC)).thenReturn(Optional.of("sk-test"));
        Graph graph = mock(Graph.class);
        when(graph.getProjectId()).thenReturn(UUID.randomUUID()); // 다른 프로젝트
        when(graphRepository.findById(graphId)).thenReturn(Optional.of(graph));

        String result = service.generateSection(projectId, graphId, userId, AiProvider.ANTHROPIC);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("커밋 SHA 또는 레포 URL을 못 찾으면 빈 문자열")
    void missingShaOrRepoUrl_returnsEmpty() {
        when(aiKeyPort.findPlainKey(userId, AiProvider.ANTHROPIC)).thenReturn(Optional.of("sk-test"));
        Graph graph = mock(Graph.class);
        when(graph.getProjectId()).thenReturn(projectId);
        when(graph.getAnalysisId()).thenReturn(analysisId);
        when(graphRepository.findById(graphId)).thenReturn(Optional.of(graph));
        when(analysisReadPort.findCommitSha(analysisId)).thenReturn(Optional.empty());

        String result = service.generateSection(projectId, graphId, userId, AiProvider.ANTHROPIC);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("대상 노드(무주석+HIGH_FAN_OUT) 없으면 빈 문자열")
    void noTargetNodes_returnsEmpty() {
        stubHappyPathPrereqs();
        when(graphQueryService.getNodes(graphId)).thenReturn(List.of());
        when(graphQueryService.getWarnings(graphId)).thenReturn(List.of());

        String result = service.generateSection(projectId, graphId, userId, AiProvider.ANTHROPIC);

        assertThat(result).isEmpty();
        verifyNoInteractions(gitHubApiClient);
    }

    @Test
    @DisplayName("무주석+HIGH_FAN_OUT 노드가 있으면 AI 요약을 생성해 섹션에 포함한다")
    void targetNode_generatesSummarySection() {
        stubHappyPathPrereqs();
        UUID nodeId = UUID.randomUUID();
        Node target = functionNode(nodeId, "processPayment", "src/Payment.java", null, 10);
        when(graphQueryService.getNodes(graphId)).thenReturn(List.of(target));
        when(graphQueryService.getEdges(graphId)).thenReturn(List.of());
        when(graphQueryService.getWarnings(graphId)).thenReturn(
                List.of(Map.of("type", "HIGH_FAN_OUT", "nodeIds", List.of(nodeId.toString()))));
        when(gitHubApiClient.fetchFileContent(anyString(), anyString(), anyString()))
                .thenReturn("line1\nline2\nvoid processPayment() { ... }\nline4\n");
        when(aiService.generate(anyString(), anyString())).thenReturn("결제를 처리하는 함수입니다.");

        String result = service.generateSection(projectId, graphId, userId, AiProvider.ANTHROPIC);

        assertThat(result).contains("processPayment").contains("결제를 처리하는 함수입니다").contains("AI 추정");
    }

    @Test
    @DisplayName("주석이 있는 노드는 HIGH_FAN_OUT이어도 스코프에서 제외된다")
    void commentedNode_excludedFromScope() {
        stubHappyPathPrereqs();
        UUID nodeId = UUID.randomUUID();
        Node target = functionNode(nodeId, "processPayment", "src/Payment.java", "이미 주석 있음", 10);
        when(graphQueryService.getNodes(graphId)).thenReturn(List.of(target));
        when(graphQueryService.getWarnings(graphId)).thenReturn(
                List.of(Map.of("type", "HIGH_FAN_OUT", "nodeIds", List.of(nodeId.toString()))));

        String result = service.generateSection(projectId, graphId, userId, AiProvider.ANTHROPIC);

        assertThat(result).isEmpty();
        verifyNoInteractions(gitHubApiClient);
    }

    @Test
    @DisplayName("스니펫 확보 실패 시 해당 노드만 건너뛰고 전체는 실패하지 않는다")
    void snippetFetchFails_skipsNodeGracefully() {
        stubHappyPathPrereqs();
        UUID nodeId = UUID.randomUUID();
        Node target = functionNode(nodeId, "processPayment", "src/Payment.java", null, 10);
        when(graphQueryService.getNodes(graphId)).thenReturn(List.of(target));
        when(graphQueryService.getEdges(graphId)).thenReturn(List.of());
        when(graphQueryService.getWarnings(graphId)).thenReturn(
                List.of(Map.of("type", "HIGH_FAN_OUT", "nodeIds", List.of(nodeId.toString()))));
        when(gitHubApiClient.fetchFileContent(anyString(), anyString(), anyString())).thenReturn(null);

        String result = service.generateSection(projectId, graphId, userId, AiProvider.ANTHROPIC);

        assertThat(result).isEmpty();
        verify(aiService, never()).generate(anyString(), anyString());
    }

    @Test
    @DisplayName("LLM 호출 예외 발생 시 해당 노드만 건너뛰고 예외를 전파하지 않는다")
    void aiCallThrows_skipsNodeGracefully() {
        stubHappyPathPrereqs();
        UUID nodeId = UUID.randomUUID();
        Node target = functionNode(nodeId, "processPayment", "src/Payment.java", null, 10);
        when(graphQueryService.getNodes(graphId)).thenReturn(List.of(target));
        when(graphQueryService.getEdges(graphId)).thenReturn(List.of());
        when(graphQueryService.getWarnings(graphId)).thenReturn(
                List.of(Map.of("type", "HIGH_FAN_OUT", "nodeIds", List.of(nodeId.toString()))));
        when(gitHubApiClient.fetchFileContent(anyString(), anyString(), anyString()))
                .thenReturn("void processPayment() { ... }");
        when(aiService.generate(anyString(), anyString())).thenThrow(new RuntimeException("API 오류"));

        String result = service.generateSection(projectId, graphId, userId, AiProvider.ANTHROPIC);

        assertThat(result).isEmpty();
    }

    private void stubHappyPathPrereqs() {
        when(aiKeyPort.findPlainKey(userId, AiProvider.ANTHROPIC)).thenReturn(Optional.of("sk-test"));
        Graph graph = mock(Graph.class);
        lenient().when(graph.getProjectId()).thenReturn(projectId);
        lenient().when(graph.getAnalysisId()).thenReturn(analysisId);
        when(graphRepository.findById(graphId)).thenReturn(Optional.of(graph));
        lenient().when(analysisReadPort.findCommitSha(analysisId)).thenReturn(Optional.of("abc123"));
        lenient().when(projectAccessPort.findGithubRepoUrl(projectId)).thenReturn(Optional.of("https://github.com/owner/repo"));
    }

    private Node functionNode(UUID id, String name, String filePath, String comment, int line) {
        Node n = mock(Node.class);
        lenient().when(n.getId()).thenReturn(id);
        lenient().when(n.getType()).thenReturn(NodeType.FUNCTION);
        lenient().when(n.getName()).thenReturn(name);
        lenient().when(n.getFilePath()).thenReturn(filePath);
        lenient().when(n.getLanguage()).thenReturn("java");
        Map<String, Object> metadata = new java.util.HashMap<>();
        metadata.put("line", line);
        if (comment != null) metadata.put("comment", comment);
        lenient().when(n.getMetadata()).thenReturn(metadata);
        return n;
    }
}
