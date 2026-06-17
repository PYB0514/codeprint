// MCP JSON-RPC 엔드포인트 단위 테스트 — 툴 스키마·각 툴 호출·에러 응답 회귀 방지
package com.codeprint.interfaces.api;

import com.codeprint.application.graph.GraphFacade;
import com.codeprint.application.graph.GraphQueryService;
import com.codeprint.domain.community.Post;
import com.codeprint.domain.community.PostRepository;
import com.codeprint.domain.graph.Edge;
import com.codeprint.domain.graph.EdgeType;
import com.codeprint.domain.graph.Graph;
import com.codeprint.domain.graph.Node;
import com.codeprint.domain.graph.NodeType;
import com.codeprint.domain.project.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class McpRpcControllerTest {

    @Mock private GraphFacade graphFacade;
    @Mock private GraphQueryService graphQueryService;
    @Mock private PostRepository postRepository;

    private McpRpcController controller;

    private final UUID graphId   = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();
    private final UUID nodeId    = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        controller = new McpRpcController(graphFacade, graphQueryService, postRepository);
    }

    @Test
    @DisplayName("initialize — protocolVersion·serverInfo 반환")
    void initialize_returnsServerInfo() {
        var req = Map.<String, Object>of("jsonrpc", "2.0", "id", 1, "method", "initialize");
        ResponseEntity<Map<String, Object>> resp = controller.handle(req);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        Map<?,?> result = (Map<?,?>) resp.getBody().get("result");
        assertThat(result.get("protocolVersion")).isEqualTo("2024-11-05");
        Map<?,?> serverInfo = (Map<?,?>) result.get("serverInfo");
        assertThat(serverInfo.get("name")).isEqualTo("codeprint");
    }

    @Test
    @DisplayName("tools/list — 5개 툴과 inputSchema 반환")
    void toolsList_returnsFiveTools() {
        var req = Map.<String, Object>of("jsonrpc", "2.0", "id", 2, "method", "tools/list");
        ResponseEntity<Map<String, Object>> resp = controller.handle(req);

        Map<?,?> result = (Map<?,?>) resp.getBody().get("result");
        List<?> tools = (List<?>) result.get("tools");
        assertThat(tools).hasSize(5);

        List<String> names = tools.stream()
                .map(t -> (String) ((Map<?,?>) t).get("name"))
                .toList();
        assertThat(names).contains(
                "search_public_projects", "get_graph_overview",
                "get_warnings", "find_nodes", "get_node_neighbors");

        // 각 툴에 inputSchema가 있는지 확인
        tools.forEach(t -> assertThat(((Map<?,?>) t).get("inputSchema")).isNotNull());
    }

    @Test
    @DisplayName("get_warnings — severity 필터 적용")
    void getWarnings_filtersBySeverity() {
        stubPublicGraph();
        List<Map<String, Object>> allWarnings = List.of(
                Map.of("type", "CYCLIC_IMPORT", "severity", "HIGH", "message", "순환 의존"),
                Map.of("type", "DEAD_CODE",     "severity", "LOW",  "message", "데드 코드")
        );
        when(graphQueryService.getWarnings(graphId)).thenReturn(allWarnings);

        var req = toolCallRequest("get_warnings", Map.of(
                "graphId",  graphId.toString(),
                "severity", "HIGH"));
        ResponseEntity<Map<String, Object>> resp = controller.handle(req);

        Map<?,?> toolResult = parseToolResult(resp);
        List<?> warnings = (List<?>) toolResult.get("content");
        // text 필드 안에 JSON이 있으므로 텍스트에 CYCLIC_IMPORT가 있고 DEAD_CODE는 없어야 함
        String text = (String) ((Map<?,?>) warnings.get(0)).get("text");
        assertThat(text).contains("CYCLIC_IMPORT").doesNotContain("DEAD_CODE");
    }

    @Test
    @DisplayName("find_nodes — 검색어 매칭 노드만 반환")
    void findNodes_returnsMatchingNodes() {
        stubPublicGraph();
        Node match   = Node.create(graphId, NodeType.FUNCTION, "UserService", "/user/UserService.java", "java");
        Node noMatch = Node.create(graphId, NodeType.FUNCTION, "OrderService", "/order/OrderService.java", "java");
        when(graphQueryService.getNodes(graphId)).thenReturn(List.of(match, noMatch));

        var req = toolCallRequest("find_nodes", Map.of(
                "graphId", graphId.toString(),
                "query",   "user"));
        ResponseEntity<Map<String, Object>> resp = controller.handle(req);

        Map<?,?> toolResult = parseToolResult(resp);
        String text = (String) ((List<?>) toolResult.get("content")).stream()
                .map(c -> (String) ((Map<?,?>) c).get("text"))
                .findFirst().orElse("");
        assertThat(text).contains("UserService").doesNotContain("OrderService");
    }

    @Test
    @DisplayName("비공개 프로젝트 접근 시 JSON-RPC 내부 오류 반환")
    void getWarnings_nonPublicProject_returnsError() {
        Graph graph = mock(Graph.class);
        when(graph.getProjectId()).thenReturn(projectId);
        when(graphQueryService.findById(graphId)).thenReturn(Optional.of(graph));
        when(graphFacade.getPublicProject(projectId))
                .thenThrow(new IllegalStateException("Project is not public"));

        var req = toolCallRequest("get_warnings", Map.of("graphId", graphId.toString()));
        ResponseEntity<Map<String, Object>> resp = controller.handle(req);

        assertThat(resp.getBody().containsKey("error")).isTrue();
        Map<?,?> error = (Map<?,?>) resp.getBody().get("error");
        assertThat(error.get("code")).isEqualTo(-32603);
    }

    @Test
    @DisplayName("알 수 없는 method — method not found 오류")
    void unknownMethod_returnsMethodNotFound() {
        var req = Map.<String, Object>of("jsonrpc", "2.0", "id", 9, "method", "unknown/method");
        ResponseEntity<Map<String, Object>> resp = controller.handle(req);

        Map<?,?> error = (Map<?,?>) resp.getBody().get("error");
        assertThat(error).isNotNull();
        assertThat(error.get("code")).isEqualTo(-32601);
    }

    // 공개 그래프 스텁 공통 설정 — project.getName/getGithubRepoUrl은 overview 전용 lenient 스텁
    private void stubPublicGraph() {
        Graph graph = mock(Graph.class);
        when(graph.getProjectId()).thenReturn(projectId);
        when(graphQueryService.findById(graphId)).thenReturn(Optional.of(graph));
        Project project = mock(Project.class);
        lenient().when(project.getName()).thenReturn("Test Project");
        lenient().when(project.getGithubRepoUrl()).thenReturn("https://github.com/test/repo");
        when(graphFacade.getPublicProject(projectId)).thenReturn(project);
    }

    // tools/call JSON-RPC 요청 빌더
    private Map<String, Object> toolCallRequest(String toolName, Map<String, Object> args) {
        return Map.of(
                "jsonrpc", "2.0",
                "id", 1,
                "method", "tools/call",
                "params", Map.of("name", toolName, "arguments", args)
        );
    }

    // tools/call 응답에서 content Map 추출
    @SuppressWarnings("unchecked")
    private Map<?,?> parseToolResult(ResponseEntity<Map<String, Object>> resp) {
        return (Map<?,?>) resp.getBody().get("result");
    }
}
