// 그래프 조회 REST API 컨트롤러
package com.codeprint.interfaces.api;

import com.codeprint.application.graph.GraphQueryService;
import com.codeprint.domain.graph.Edge;
import com.codeprint.domain.graph.Graph;
import com.codeprint.domain.graph.Node;
import com.codeprint.domain.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class GraphController {

    private final GraphQueryService graphQueryService;

    @GetMapping("/{projectId}/graph")
    public ResponseEntity<?> getGraph(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal User user) {

        return graphQueryService.findLatestByProject(projectId)
                .map(graph -> {
                    List<Node> nodes = graphQueryService.getNodes(graph.getId());
                    List<Edge> edges = graphQueryService.getEdges(graph.getId());

                    List<Map<String, Object>> nodeData = nodes.stream()
                            .filter(n -> !n.isHidden())
                            .map(n -> Map.<String, Object>of(
                                    "id", n.getId().toString(),
                                    "type", n.getType().name(),
                                    "name", n.getName(),
                                    "filePath", n.getFilePath() != null ? n.getFilePath() : "",
                                    "language", n.getLanguage() != null ? n.getLanguage() : "",
                                    "posX", n.getPosX(),
                                    "posY", n.getPosY()
                            ))
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
}
