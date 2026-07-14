// AI 에이전트가 그래프 구조를 MCP 컨텍스트로 조회하는 엔드포인트
package com.codeprint.interfaces.api;

import com.codeprint.application.admin.AdminDigestService;
import com.codeprint.application.admin.Digest;
import com.codeprint.application.graph.GraphFacade;
import com.codeprint.application.graph.GraphQueryService;
import com.codeprint.application.team.TeamProjectAccessService;
import com.codeprint.domain.graph.Edge;
import com.codeprint.domain.graph.Graph;
import com.codeprint.domain.graph.Node;
import com.codeprint.domain.graph.NodeType;
import com.codeprint.domain.graph.port.ProjectAccessPort.ProjectAccessView;
import com.codeprint.domain.team.TeamApiKeyPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/mcp")
@RequiredArgsConstructor
public class McpController {

    private final GraphFacade graphFacade;
    private final GraphQueryService graphQueryService;
    private final AdminDigestService adminDigestService;
    private final TeamProjectAccessService teamProjectAccessService;

    // 관리자 전용 — 최신 일일 다이제스트를 MCP 컨텍스트로 반환 (수익·사용자 수 민감, ADMIN 인증 필수)
    @GetMapping("/admin/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getAdminStats() {
        Optional<Digest> digest = adminDigestService.latestStoredDigest();
        return digest.isPresent()
                ? ResponseEntity.ok(digest.get())
                : ResponseEntity.ok(Map.of("message", "아직 집계된 다이제스트가 없습니다"));
    }

    // 공개 프로젝트의 그래프를 MCP 컨텍스트 형식으로 반환 (summary=true 이면 상위 50개 함수만 반환)
    @GetMapping("/graphs/{graphId}/context")
    public ResponseEntity<Map<String, Object>> getGraphContext(
            @PathVariable UUID graphId,
            @RequestParam(defaultValue = "false") boolean summary) {
        Graph graph = graphQueryService.findById(graphId).orElse(null);
        if (graph == null) return ResponseEntity.notFound().build();

        // 공개 프로젝트 검증 (비공개이면 403)
        ProjectAccessView project;
        try {
            project = graphFacade.getPublicProject(graph.getProjectId());
        } catch (Exception e) {
            return ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(buildContextResponse(graph, project, summary));
    }

    // 팀 API 키가 속한 팀에 배분된 프로젝트 목록 — 에이전트가 교차 조회 대상을 발견하는 진입점
    @GetMapping("/team/projects")
    @PreAuthorize("hasRole('TEAM_API_KEY')")
    public ResponseEntity<List<Map<String, Object>>> getTeamProjects(
            @AuthenticationPrincipal TeamApiKeyPrincipal principal) {
        List<Map<String, Object>> result = teamProjectAccessService.getAllocatedProjectIds(principal.teamId()).stream()
                .map(projectId -> {
                    ProjectAccessView project = graphFacade.getProjectById(projectId).orElse(null);
                    if (project == null) return null;
                    UUID latestGraphId = graphQueryService.findLatestByProject(projectId)
                            .map(Graph::getId).orElse(null);
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("projectId", projectId.toString());
                    m.put("projectName", project.name());
                    m.put("repoUrl", project.githubRepoUrl());
                    m.put("latestGraphId", latestGraphId != null ? latestGraphId.toString() : null);
                    return m;
                })
                .filter(Objects::nonNull)
                .toList();
        return ResponseEntity.ok(result);
    }

    // 팀에 배분된(비공개 포함) 프로젝트의 그래프를 MCP 컨텍스트 형식으로 반환 — 인가 기준은 isPublic 대신 팀 배분 여부
    @GetMapping("/team/graphs/{graphId}/context")
    @PreAuthorize("hasRole('TEAM_API_KEY')")
    public ResponseEntity<Map<String, Object>> getTeamGraphContext(
            @PathVariable UUID graphId,
            @RequestParam(defaultValue = "false") boolean summary,
            @AuthenticationPrincipal TeamApiKeyPrincipal principal) {
        Graph graph = graphQueryService.findById(graphId).orElse(null);
        if (graph == null) return ResponseEntity.notFound().build();

        if (!teamProjectAccessService.isAllocatedToTeam(graph.getProjectId(), principal.teamId())) {
            return ResponseEntity.status(403).build();
        }
        ProjectAccessView project = graphFacade.getProjectById(graph.getProjectId()).orElse(null);
        if (project == null) return ResponseEntity.notFound().build();

        return ResponseEntity.ok(buildContextResponse(graph, project, summary));
    }

    // 그래프 컨텍스트 응답 조립 — 공개용·팀 교차조회용 엔드포인트가 인가 검증 후 공통으로 사용
    private Map<String, Object> buildContextResponse(Graph graph, ProjectAccessView project, boolean summary) {
        UUID graphId = graph.getId();
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
        response.put("projectId", project.id().toString());
        response.put("projectName", project.name());
        response.put("repoUrl", project.githubRepoUrl());
        response.put("stats", stats);
        response.put("nodes", nodeSummaries);
        response.put("edges", edgeSummaries);
        if (summary) response.put("truncated", allNodes.size() > nodes.size());

        return response;
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
