// MCP JSON-RPC 2.0 엔드포인트 — AI 에이전트가 공개 그래프를 조회하는 stateless 서버
package com.codeprint.interfaces.api;

import com.codeprint.application.graph.GraphFacade;
import com.codeprint.application.graph.GraphQueryService;
import com.codeprint.domain.community.Post;
import com.codeprint.domain.community.PostRepository;
import com.codeprint.domain.graph.Graph;
import com.codeprint.domain.graph.Node;
import com.codeprint.domain.graph.NodeType;
import com.codeprint.domain.project.Project;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class McpRpcController {

    private static final String JSONRPC = "2.0";
    private static final String MCP_VERSION = "2024-11-05";

    private static final int ERR_METHOD_NOT_FOUND = -32601;
    private static final int ERR_INVALID_PARAMS   = -32602;
    private static final int ERR_INTERNAL         = -32603;

    private final GraphFacade graphFacade;
    private final GraphQueryService graphQueryService;
    private final PostRepository postRepository;

    // MCP JSON-RPC 2.0 단일 엔드포인트 — stateless, 세션 없음
    @PostMapping("/mcp/rpc")
    public ResponseEntity<Map<String, Object>> handle(@RequestBody Map<String, Object> req) {
        Object id = req.get("id");
        String method = req.get("method") instanceof String s ? s : "";

        try {
            Object result = switch (method) {
                case "initialize"               -> buildInitializeResult();
                case "notifications/initialized" -> Map.of();
                case "tools/list"               -> buildToolsList();
                case "tools/call"               -> handleToolCall(req);
                default -> throw new McpException(ERR_METHOD_NOT_FOUND, "Method not found: " + method);
            };
            return ResponseEntity.ok(rpcResult(id, result));
        } catch (McpException e) {
            return ResponseEntity.ok(rpcError(id, e.code, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.ok(rpcError(id, ERR_INTERNAL, e.getMessage()));
        }
    }

    // initialize 응답 — 클라이언트가 처음 연결할 때 서버 정보를 반환
    private Map<String, Object> buildInitializeResult() {
        return Map.of(
                "protocolVersion", MCP_VERSION,
                "capabilities", Map.of("tools", Map.of()),
                "serverInfo", Map.of("name", "codeprint", "version", "0.86.17")
        );
    }

    // tools/list 응답 — 제공하는 5개 툴의 스키마를 반환
    private Map<String, Object> buildToolsList() {
        List<Map<String, Object>> tools = List.of(
                buildTool("search_public_projects",
                        "공개 공유된 프로젝트 목록 검색. 제목·레포 URL로 필터 가능.",
                        Map.of("query", prop("string", "검색어 (선택)")),
                        List.of()),

                buildTool("get_graph_overview",
                        "그래프 개요 — 프로젝트 정보·파일/함수 통계·언어 분포·경고 수 반환.",
                        Map.of("graphId", prop("string", "조회할 그래프 UUID")),
                        List.of("graphId")),

                buildTool("get_warnings",
                        "그래프 경고 목록 — 각 경고에 구체적인 수정 가이드 포함.",
                        Map.of(
                                "graphId",  prop("string", "조회할 그래프 UUID"),
                                "severity", prop("string", "HIGH | MEDIUM | LOW 필터 (선택)")),
                        List.of("graphId")),

                buildTool("find_nodes",
                        "노드 이름·파일 경로로 검색. 함수·파일·DB 테이블·API 엔드포인트 포함.",
                        Map.of(
                                "graphId", prop("string", "조회할 그래프 UUID"),
                                "query",   prop("string", "검색어"),
                                "limit",   prop("integer", "최대 반환 수 (기본 20)")),
                        List.of("graphId", "query")),

                buildTool("get_node_neighbors",
                        "특정 노드의 인바운드·아웃바운드 이웃 노드와 엣지 타입 반환.",
                        Map.of(
                                "graphId", prop("string", "그래프 UUID"),
                                "nodeId",  prop("string", "노드 UUID")),
                        List.of("graphId", "nodeId"))
        );
        return Map.of("tools", tools);
    }

    // tools/call 라우팅 — 툴 이름으로 핸들러 분기
    @SuppressWarnings("unchecked")
    private Map<String, Object> handleToolCall(Map<String, Object> req) {
        Map<String, Object> params = req.get("params") instanceof Map<?,?> m
                ? (Map<String, Object>) m : Map.of();
        String toolName = params.get("name") instanceof String s ? s : "";
        Map<String, Object> args = params.get("arguments") instanceof Map<?,?> m
                ? (Map<String, Object>) m : Map.of();

        Object content = switch (toolName) {
            case "search_public_projects" -> toolSearchPublicProjects(args);
            case "get_graph_overview"     -> toolGetGraphOverview(args);
            case "get_warnings"           -> toolGetWarnings(args);
            case "find_nodes"             -> toolFindNodes(args);
            case "get_node_neighbors"     -> toolGetNodeNeighbors(args);
            default -> throw new McpException(ERR_METHOD_NOT_FOUND, "Unknown tool: " + toolName);
        };

        return Map.of(
                "content", List.of(Map.of("type", "text", "text", toJson(content))),
                "isError", false
        );
    }

    // 공개 공유된 프로젝트 목록 반환 — 그래프가 첨부된 게시글 기반
    private List<Map<String, Object>> toolSearchPublicProjects(Map<String, Object> args) {
        String query = args.get("query") instanceof String s ? s.toLowerCase() : null;

        List<Post> posts = postRepository.findWithGraphOrSnapshots(PageRequest.of(0, 50));

        return posts.stream()
                .filter(p -> query == null
                        || (p.getTitle() != null && p.getTitle().toLowerCase().contains(query))
                        || (p.getRepoUrl() != null && p.getRepoUrl().toLowerCase().contains(query)))
                .limit(20)
                .map(p -> resolvePublicProjectEntry(p))
                .filter(Objects::nonNull)
                .toList();
    }

    // 게시글의 그래프 → 프로젝트 정보 조회 — 비공개 프로젝트이면 null 반환 (레거시 단일 첨부 또는 신규 스냅샷 중 첫 번째)
    private Map<String, Object> resolvePublicProjectEntry(Post post) {
        UUID graphId = post.getGraphId();
        if (graphId == null) {
            graphId = postRepository.findSnapshotsByPostId(post.getId()).stream()
                    .findFirst()
                    .map(com.codeprint.domain.community.PostGraphSnapshot::getGraphId)
                    .orElse(null);
        }
        if (graphId == null) return null;

        Optional<Graph> graphOpt = graphQueryService.findById(graphId);
        if (graphOpt.isEmpty()) return null;

        UUID projectId = graphOpt.get().getProjectId();
        try {
            Project project = graphFacade.getPublicProject(projectId);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("projectId", projectId.toString());
            entry.put("name", project.getName());
            entry.put("repoUrl", project.getGithubRepoUrl());
            entry.put("latestGraphId", graphId.toString());
            entry.put("postTitle", post.getTitle());
            return entry;
        } catch (Exception e) {
            return null;
        }
    }

    // 그래프 개요 — 노드 통계·언어 분포·경고 수 반환
    private Map<String, Object> toolGetGraphOverview(Map<String, Object> args) {
        UUID graphId = requireGraphId(args);
        Graph graph = graphQueryService.findById(graphId)
                .orElseThrow(() -> new McpException(ERR_INVALID_PARAMS, "Graph not found: " + graphId));

        Project project = graphFacade.getPublicProject(graph.getProjectId());

        List<Node> nodes = graphQueryService.getNodes(graphId).stream()
                .filter(n -> !n.isHidden()).toList();

        Map<String, Long> stats = new LinkedHashMap<>();
        stats.put("files",        nodes.stream().filter(n -> n.getType() == NodeType.FILE).count());
        stats.put("functions",    nodes.stream().filter(n -> n.getType() == NodeType.FUNCTION).count());
        stats.put("dbTables",     nodes.stream().filter(n -> n.getType() == NodeType.DB_TABLE).count());
        stats.put("apiEndpoints", nodes.stream().filter(n -> n.getType() == NodeType.API_ENDPOINT).count());

        Map<String, Long> languages = nodes.stream()
                .filter(n -> n.getLanguage() != null)
                .collect(Collectors.groupingBy(Node::getLanguage, Collectors.counting()));

        List<Map<String, Object>> warnings = graphQueryService.getWarnings(graphId);
        Map<String, Long> warningCounts = new LinkedHashMap<>();
        warningCounts.put("HIGH",   warnings.stream().filter(w -> "HIGH".equals(w.get("severity"))).count());
        warningCounts.put("MEDIUM", warnings.stream().filter(w -> "MEDIUM".equals(w.get("severity"))).count());
        warningCounts.put("LOW",    warnings.stream().filter(w -> "LOW".equals(w.get("severity"))).count());
        warningCounts.put("total",  (long) warnings.size());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectName",   project.getName());
        result.put("repoUrl",       project.getGithubRepoUrl());
        result.put("stats",         stats);
        result.put("languages",     languages);
        result.put("warningCounts", warningCounts);
        return result;
    }

    // 경고 목록 반환 — severity 필터 선택 적용
    private List<Map<String, Object>> toolGetWarnings(Map<String, Object> args) {
        UUID graphId = requireGraphId(args);
        verifyPublicGraph(graphId);

        String severityFilter = args.get("severity") instanceof String s ? s.toUpperCase() : null;

        return graphQueryService.getWarnings(graphId).stream()
                .filter(w -> severityFilter == null || severityFilter.equals(w.get("severity")))
                .map(w -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("type",     w.get("type"));
                    item.put("severity", w.get("severity"));
                    item.put("message",  w.get("message"));
                    if (w.get("file") != null) item.put("file", w.get("file"));
                    return item;
                })
                .toList();
    }

    // 노드 이름·파일 경로 검색 — limit 기본 20
    private List<Map<String, Object>> toolFindNodes(Map<String, Object> args) {
        UUID graphId = requireGraphId(args);
        verifyPublicGraph(graphId);

        String query = args.get("query") instanceof String s ? s.toLowerCase() : "";
        int limit = args.get("limit") instanceof Number n ? n.intValue() : 20;
        if (limit < 1 || limit > 100) limit = 20;

        return graphQueryService.getNodes(graphId).stream()
                .filter(n -> !n.isHidden())
                .filter(n -> n.getName().toLowerCase().contains(query)
                        || (n.getFilePath() != null && n.getFilePath().toLowerCase().contains(query)))
                .limit(limit)
                .map(n -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id",   n.getId().toString());
                    item.put("type", n.getType().name());
                    item.put("name", n.getName());
                    if (n.getFilePath() != null) item.put("filePath", n.getFilePath());
                    if (n.getLanguage() != null) item.put("language", n.getLanguage());
                    if (n.getMetadata() != null && n.getMetadata().get("comment") != null)
                        item.put("comment", n.getMetadata().get("comment"));
                    return item;
                })
                .toList();
    }

    // 특정 노드의 인바운드·아웃바운드 이웃 반환
    private Map<String, Object> toolGetNodeNeighbors(Map<String, Object> args) {
        UUID graphId = requireGraphId(args);
        verifyPublicGraph(graphId);

        UUID nodeId = requireUUID(args, "nodeId");

        List<Node> nodes = graphQueryService.getNodes(graphId);
        Map<UUID, Node> nodeMap = nodes.stream()
                .collect(Collectors.toMap(Node::getId, n -> n));

        Node target = nodeMap.get(nodeId);
        if (target == null) throw new McpException(ERR_INVALID_PARAMS, "Node not found: " + nodeId);

        Map<String, Object> nodeView = new LinkedHashMap<>();
        nodeView.put("id",   target.getId().toString());
        nodeView.put("type", target.getType().name());
        nodeView.put("name", target.getName());
        if (target.getFilePath() != null) nodeView.put("filePath", target.getFilePath());

        List<Map<String, Object>> inbound = new ArrayList<>();
        List<Map<String, Object>> outbound = new ArrayList<>();

        graphQueryService.getEdges(graphId).stream()
                .filter(e -> !e.isHidden())
                .forEach(e -> {
                    if (e.getTargetNodeId().equals(nodeId)) {
                        Node from = nodeMap.get(e.getSourceNodeId());
                        if (from != null) {
                            Map<String, Object> item = new LinkedHashMap<>();
                            item.put("edgeType", e.getType().name());
                            item.put("fromId",   from.getId().toString());
                            item.put("fromName", from.getName());
                            if (from.getFilePath() != null) item.put("fromFile", from.getFilePath());
                            inbound.add(item);
                        }
                    } else if (e.getSourceNodeId().equals(nodeId)) {
                        Node to = nodeMap.get(e.getTargetNodeId());
                        if (to != null) {
                            Map<String, Object> item = new LinkedHashMap<>();
                            item.put("edgeType", e.getType().name());
                            item.put("toId",     to.getId().toString());
                            item.put("toName",   to.getName());
                            if (to.getFilePath() != null) item.put("toFile", to.getFilePath());
                            outbound.add(item);
                        }
                    }
                });

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("node",    nodeView);
        result.put("inbound", inbound);
        result.put("outbound", outbound);
        return result;
    }

    // graphId args 파싱 + 공개 검증을 묶은 헬퍼
    private void verifyPublicGraph(UUID graphId) {
        Graph graph = graphQueryService.findById(graphId)
                .orElseThrow(() -> new McpException(ERR_INVALID_PARAMS, "Graph not found: " + graphId));
        graphFacade.getPublicProject(graph.getProjectId());
    }

    // args에서 graphId UUID 추출
    private UUID requireGraphId(Map<String, Object> args) {
        return requireUUID(args, "graphId");
    }

    // args에서 UUID 문자열 파싱
    private UUID requireUUID(Map<String, Object> args, String key) {
        if (!(args.get(key) instanceof String s)) {
            throw new McpException(ERR_INVALID_PARAMS, "Missing or invalid: " + key);
        }
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            throw new McpException(ERR_INVALID_PARAMS, "Invalid UUID for " + key + ": " + s);
        }
    }

    // JSON-RPC 성공 응답 조립
    private Map<String, Object> rpcResult(Object id, Object result) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("jsonrpc", JSONRPC);
        r.put("id", id);
        r.put("result", result);
        return r;
    }

    // JSON-RPC 오류 응답 조립
    private Map<String, Object> rpcError(Object id, int code, String message) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("jsonrpc", JSONRPC);
        r.put("id", id);
        r.put("error", Map.of("code", code, "message", message != null ? message : "Internal error"));
        return r;
    }

    // 툴 정의 빌더 헬퍼
    private Map<String, Object> buildTool(String name, String description,
                                          Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        if (!required.isEmpty()) schema.put("required", required);

        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("name",        name);
        tool.put("description", description);
        tool.put("inputSchema", schema);
        return tool;
    }

    // 프로퍼티 정의 빌더 헬퍼
    private Map<String, Object> prop(String type, String description) {
        return Map.of("type", type, "description", description);
    }

    // JSON 직렬화 — Jackson 없이 Map/List 구조를 직접 Jackson ObjectMapper로 직렬화
    private String toJson(Object obj) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    // MCP 처리 중 발생하는 JSON-RPC 오류를 표현하는 내부 예외
    private static class McpException extends RuntimeException {
        final int code;
        McpException(int code, String message) {
            super(message);
            this.code = code;
        }
    }
}
