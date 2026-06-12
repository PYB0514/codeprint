// AI 에이전트가 그래프 구조를 MCP 컨텍스트로 조회하는 엔드포인트
package com.codeprint.interfaces.api;

import com.codeprint.application.graph.GraphFacade;
import com.codeprint.application.graph.GraphQueryService;
import com.codeprint.domain.graph.Edge;
import com.codeprint.domain.graph.Node;
import com.codeprint.domain.graph.NodeType;
import com.codeprint.domain.project.Project;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/mcp")
@RequiredArgsConstructor
public class McpController {

    private final GraphFacade graphFacade;
    private final GraphQueryService graphQueryService;

    // 공개 프로젝트의 그래프를 MCP 컨텍스트 형식으로 반환 (summary=true 이면 상위 50개 함수만 반환)
    @GetMapping("/graphs/{graphId}/context")
    public ResponseEntity<Map<String, Object>> getGraphContext(
            @PathVariable UUID graphId,
            @RequestParam(defaultValue = "false") boolean summary) {
        // 그래프 존재 확인
        var graph = graphQueryService.findById(graphId)
                .orElse(null);
        if (graph == null) return ResponseEntity.notFound().build();

        // 공개 프로젝트 검증 (비공개이면 403)
        Project project;
        try {
            project = graphFacade.getPublicProject(graph.getProjectId());
        } catch (Exception e) {
            return ResponseEntity.status(403).build();
        }

        List<Node> allNodes = graphQueryService.getNodes(graphId).stream()
                .filter(n -> !n.isHidden())
                .toList();
        List<Edge> allEdges = graphQueryService.getEdges(graphId).stream()
                .filter(e -> !e.isHidden())
                .toList();

        // summary 모드: FILE·FUNCTION 상위 50개 + DB_TABLE·API_ENDPOINT 전체
        List<Node> nodes = summary
                ? buildSummaryNodes(allNodes)
                : allNodes;
        List<Edge> edges = summary
                ? filterEdgesForNodes(allEdges, nodes)
                : allEdges;

        // 노드 요약 — AI 컨텍스트에 필요한 핵심 필드만 포함
        List<Map<String, Object>> nodeSummaries = nodes.stream().map(n -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", n.getId().toString());
            m.put("type", n.getType().name());
            m.put("name", n.getName());
            if (n.getFilePath() != null) m.put("filePath", n.getFilePath());
            if (n.getLanguage() != null) m.put("language", n.getLanguage());
            if (n.getMetadata() != null && n.getMetadata().containsKey("comment")) {
                m.put("comment", n.getMetadata().get("comment"));
            }
            if (n.getUserLabel() != null) m.put("label", n.getUserLabel());
            if (n.getUserNote() != null) m.put("note", n.getUserNote());
            return m;
        }).toList();

        // 엣지 요약
        Map<UUID, String> nodeIdToName = new HashMap<>();
        nodes.forEach(n -> nodeIdToName.put(n.getId(), n.getName()));

        List<Map<String, Object>> edgeSummaries = edges.stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.getId().toString());
            m.put("type", e.getType().name());
            m.put("source", e.getSourceNodeId().toString());
            m.put("sourceName", nodeIdToName.getOrDefault(e.getSourceNodeId(), ""));
            m.put("target", e.getTargetNodeId().toString());
            m.put("targetName", nodeIdToName.getOrDefault(e.getTargetNodeId(), ""));
            return m;
        }).toList();

        // 통계
        Map<String, Long> stats = new LinkedHashMap<>();
        stats.put("files", nodes.stream().filter(n -> n.getType() == NodeType.FILE).count());
        stats.put("functions", nodes.stream().filter(n -> n.getType() == NodeType.FUNCTION).count());
        stats.put("dbTables", nodes.stream().filter(n -> n.getType() == NodeType.DB_TABLE).count());
        stats.put("apiEndpoints", nodes.stream().filter(n -> n.getType() == NodeType.API_ENDPOINT).count());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("graphId", graphId.toString());
        response.put("projectId", project.getId().toString());
        response.put("projectName", project.getName());
        response.put("repoUrl", project.getGithubRepoUrl());
        response.put("stats", stats);
        response.put("nodes", nodeSummaries);
        response.put("edges", edgeSummaries);
        if (summary) response.put("truncated", allNodes.size() > nodes.size());

        return ResponseEntity.ok(response);
    }

    // summary 모드용: DB/API 전체 + FILE/FUNCTION 상위 50개
    private List<Node> buildSummaryNodes(List<Node> all) {
        List<Node> priority = all.stream()
                .filter(n -> n.getType() == NodeType.DB_TABLE || n.getType() == NodeType.API_ENDPOINT)
                .toList();
        List<Node> rest = all.stream()
                .filter(n -> n.getType() == NodeType.FILE || n.getType() == NodeType.FUNCTION)
                .limit(50)
                .toList();
        List<Node> combined = new ArrayList<>(priority);
        combined.addAll(rest);
        return combined;
    }

    // summary 모드용: 포함된 노드끼리의 엣지만 필터
    private List<Edge> filterEdgesForNodes(List<Edge> edges, List<Node> nodes) {
        Set<UUID> nodeIds = new HashSet<>();
        nodes.forEach(n -> nodeIds.add(n.getId()));
        return edges.stream()
                .filter(e -> nodeIds.contains(e.getSourceNodeId()) && nodeIds.contains(e.getTargetNodeId()))
                .toList();
    }
}
