// 그래프 조회 REST API 컨트롤러
package com.codeprint.interfaces.api;

import com.codeprint.application.analysis.AnalysisApplicationService;
import com.codeprint.application.graph.GraphCommandService;
import com.codeprint.application.graph.GraphQueryService;
import com.codeprint.application.project.ProjectQueryService;
import com.codeprint.domain.graph.Edge;
import com.codeprint.domain.graph.Graph;
import com.codeprint.domain.graph.Node;
import com.codeprint.domain.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class GraphController {

    private final GraphQueryService graphQueryService;
    private final GraphCommandService graphCommandService;
    private final ProjectQueryService projectQueryService;
    private final AnalysisApplicationService analysisApplicationService;

    // 프로젝트의 그래프 버전 목록을 최신순으로 조회
    @GetMapping("/api/projects/{projectId}/graphs")
    public ResponseEntity<List<Map<String, Object>>> getGraphVersions(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal User user) {
        projectQueryService.getProject(projectId, user.getId());
        List<Map<String, Object>> versions = graphQueryService.findAllByProject(projectId).stream()
                .map(graph -> {
                    String branch = analysisApplicationService.getAnalysis(graph.getAnalysisId())
                            .getBranch();
                    Map<String, Object> item = new java.util.LinkedHashMap<>();
                    item.put("graphId", graph.getId().toString());
                    item.put("createdAt", graph.getCreatedAt().toString());
                    item.put("branch", branch != null ? branch : "default");
                    return item;
                })
                .toList();
        return ResponseEntity.ok(versions);
    }

    // 프로젝트의 최신 그래프(노드+엣지)를 조회 — graphId 지정 시 해당 버전 반환
    @GetMapping("/api/projects/{projectId}/graph")
    public ResponseEntity<?> getGraph(
            @PathVariable UUID projectId,
            @RequestParam(required = false) UUID graphId,
            @AuthenticationPrincipal User user) {

        Optional<Graph> graphOpt = graphId != null
                ? graphQueryService.findById(graphId)
                : graphQueryService.findLatestByProject(projectId);

        return graphOpt.map(graph -> {
                    List<Node> nodes = graphQueryService.getNodes(graph.getId());
                    List<Edge> edges = graphQueryService.getEdges(graph.getId());

                    List<Map<String, Object>> nodeData = nodes.stream()
                            .filter(n -> !n.isHidden())
                            .map(n -> {
                                Map<String, Object> node = new java.util.LinkedHashMap<>();
                                node.put("id", n.getId().toString());
                                node.put("type", n.getType().name());
                                node.put("name", n.getName());
                                node.put("filePath", n.getFilePath() != null ? n.getFilePath() : "");
                                node.put("language", n.getLanguage() != null ? n.getLanguage() : "");
                                node.put("posX", n.getPosX());
                                node.put("posY", n.getPosY());
                                if (n.getMetadata() != null && n.getMetadata().containsKey("comment")) {
                                    node.put("comment", n.getMetadata().get("comment"));
                                }
                                return node;
                            })
                            .toList();

                    List<Map<String, Object>> edgeData = edges.stream()
                            .filter(e -> !e.isHidden())
                            .map(e -> Map.<String, Object>of(
                                    "id", e.getId().toString(),
                                    "type", e.getType().name(),
                                    "source", e.getSourceNodeId().toString(),
                                    "target", e.getTargetNodeId().toString(),
                                    "edgeIdentifier", e.getEdgeIdentifier()
                            ))
                            .toList();

                    return ResponseEntity.ok(Map.of(
                            "graphId", graph.getId().toString(),
                            "nodes", nodeData,
                            "edges", edgeData
                    ));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // 공개 프로젝트의 그래프를 비인증으로 조회
    @GetMapping("/api/share/{projectId}/graph")
    public ResponseEntity<?> getPublicGraph(@PathVariable UUID projectId) {
        projectQueryService.getPublicProject(projectId);
        return graphQueryService.findLatestByProject(projectId)
                .map(graph -> {
                    List<Node> nodes = graphQueryService.getNodes(graph.getId());
                    List<Edge> edges = graphQueryService.getEdges(graph.getId());

                    List<Map<String, Object>> nodeData = nodes.stream()
                            .filter(n -> !n.isHidden())
                            .map(n -> {
                                Map<String, Object> node = new java.util.LinkedHashMap<>();
                                node.put("id", n.getId().toString());
                                node.put("type", n.getType().name());
                                node.put("name", n.getName());
                                node.put("filePath", n.getFilePath() != null ? n.getFilePath() : "");
                                node.put("language", n.getLanguage() != null ? n.getLanguage() : "");
                                node.put("posX", n.getPosX());
                                node.put("posY", n.getPosY());
                                if (n.getMetadata() != null && n.getMetadata().containsKey("comment")) {
                                    node.put("comment", n.getMetadata().get("comment"));
                                }
                                return node;
                            })
                            .toList();

                    List<Map<String, Object>> edgeData = edges.stream()
                            .filter(e -> !e.isHidden())
                            .map(e -> Map.<String, Object>of(
                                    "id", e.getId().toString(),
                                    "type", e.getType().name(),
                                    "source", e.getSourceNodeId().toString(),
                                    "target", e.getTargetNodeId().toString(),
                                    "edgeIdentifier", e.getEdgeIdentifier()
                            ))
                            .toList();

                    return ResponseEntity.ok(Map.of(
                            "graphId", graph.getId().toString(),
                            "nodes", nodeData,
                            "edges", edgeData
                    ));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // 노드 드래그 후 위치를 저장 — 그래프 소유자만 가능
    @PutMapping("/api/graphs/{graphId}/nodes/{nodeId}/position")
    public ResponseEntity<Void> updateNodePosition(
            @PathVariable UUID graphId,
            @PathVariable UUID nodeId,
            @RequestBody Map<String, Double> body,
            @AuthenticationPrincipal User user) {

        graphQueryService.findById(graphId)
                .ifPresent(graph -> projectQueryService.getProject(graph.getProjectId(), user.getId()));

        double x = body.getOrDefault("x", 0.0);
        double y = body.getOrDefault("y", 0.0);
        graphCommandService.updateNodePosition(nodeId, x, y);
        return ResponseEntity.ok().build();
    }
}
