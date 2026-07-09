// MCP JSON-RPC 2.0 엔드포인트 — AI 에이전트가 공개 그래프를 조회하는 stateless 서버
package com.codeprint.interfaces.api;

import com.codeprint.application.graph.GraphFacade;
import com.codeprint.application.graph.GraphQueryService;
import com.codeprint.application.graph.RepoMapService;
import com.codeprint.domain.graph.Graph;
import com.codeprint.domain.graph.Node;
import com.codeprint.domain.graph.NodeType;
import com.codeprint.domain.project.Project;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
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
    private final RepoMapService repoMapService;

    // MCP JSON-RPC 2.0 단일 엔드포인트 — stateless, 세션 없음
    @PostMapping("/mcp/rpc")
    public ResponseEntity<Map<String, Object>> handle(@RequestBody Map<String, Object> req, HttpServletRequest httpReq) {
        Object id = req.get("id");
        String method = req.get("method") instanceof String s ? s : "";

        try {
            Object result = switch (method) {
                case "initialize"               -> buildInitializeResult(httpReq.getHeader("User-Agent"));
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
    private Map<String, Object> buildInitializeResult(String userAgent) {
        log.info("[MCP] initialize from User-Agent: {}", userAgent);
        return Map.of(
                "protocolVersion", MCP_VERSION,
                "capabilities", Map.of("tools", Map.of()),
                "serverInfo", Map.of("name", "codeprint", "version", "0.121.1")
        );
    }

    // tools/list 응답 — 제공하는 6개 툴의 스키마를 반환
    private Map<String, Object> buildToolsList() {
        List<Map<String, Object>> tools = List.of(
                buildTool("search_public_projects",
                        "Search public projects by name or GitHub repo URL. Returns each project's latest graph ID for use with the other tools.",
                        Map.of("query", prop("string", "Search query (optional) — matches project name or repo URL")),
                        List.of()),

                buildTool("get_graph_overview",
                        "Graph overview — project info, file/function stats, language distribution, and warning counts.",
                        Map.of("graphId", prop("string", "Graph UUID to query")),
                        List.of("graphId")),

                buildTool("get_warnings",
                        "List of structural warnings for a graph, each with an actionable fix suggestion.",
                        Map.of(
                                "graphId",  prop("string", "Graph UUID to query"),
                                "severity", prop("string", "Filter by HIGH | MEDIUM | LOW (optional)")),
                        List.of("graphId")),

                buildTool("find_nodes",
                        "Search nodes by name or file path. Includes functions, files, DB tables, and API endpoints.",
                        Map.of(
                                "graphId", prop("string", "Graph UUID to query"),
                                "query",   prop("string", "Search query"),
                                "limit",   prop("integer", "Max results to return (default 20)")),
                        List.of("graphId", "query")),

                buildTool("get_node_neighbors",
                        "Inbound and outbound neighbor nodes and edge types for a specific node.",
                        Map.of(
                                "graphId", prop("string", "Graph UUID"),
                                "nodeId",  prop("string", "Node UUID")),
                        List.of("graphId", "nodeId")),

                buildTool("get_repo_map",
                        "One-call repo map — a file/function tree in Markdown with role comments, in a single response instead of many file reads.",
                        Map.of("graphId", prop("string", "Graph UUID to query")),
                        List.of("graphId"))
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
            case "get_repo_map"           -> toolGetRepoMap(args);
            default -> throw new McpException(ERR_METHOD_NOT_FOUND, "Unknown tool: " + toolName);
        };

        return Map.of(
                "content", List.of(Map.of("type", "text", "text", toJson(content))),
                "isError", false
        );
    }

    // 공개 프로젝트 목록 반환 — 게시글 첨부 여부와 무관하게 공개(isPublic) 프로젝트 전체가 검색 대상
    private List<Map<String, Object>> toolSearchPublicProjects(Map<String, Object> args) {
        String query = args.get("query") instanceof String s ? s : null;

        return graphFacade.searchPublicProjects(query).stream()
                .limit(20)
                .map(this::toPublicProjectEntry)
                .filter(Objects::nonNull)
                .toList();
    }

    // 공개 프로젝트 → MCP 응답 엔트리 변환 — 실제 최신 그래프 조회, 그래프가 없으면(분석 이력 없음) 제외
    private Map<String, Object> toPublicProjectEntry(Project project) {
        return graphQueryService.findLatestByProject(project.getId())
                .map(graph -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("projectId", project.getId().toString());
                    entry.put("name", project.getName());
                    entry.put("repoUrl", project.getGithubRepoUrl());
                    entry.put("latestGraphId", graph.getId().toString());
                    return entry;
                })
                .orElse(null);
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

    // 파일/함수 트리 마크다운 1콜 반환 — 웹 다운로드("AI 컨텍스트")와 동일 생성기(RepoMapService) 사용
    private String toolGetRepoMap(Map<String, Object> args) {
        UUID graphId = requireGraphId(args);
        verifyPublicGraph(graphId);

        List<Node> nodes = graphQueryService.getNodes(graphId).stream()
                .filter(n -> !n.isHidden()).toList();
        return repoMapService.generate(nodes);
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
