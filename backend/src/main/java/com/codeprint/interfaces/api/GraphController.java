// 그래프 조회 REST API 컨트롤러
package com.codeprint.interfaces.api;

import com.codeprint.application.graph.GraphCommandService;
import com.codeprint.application.graph.GraphDiffService;
import com.codeprint.application.graph.GraphFacade;
import com.codeprint.application.graph.GraphQueryService;
import com.codeprint.application.graph.GraphWarningService;
import com.codeprint.application.graph.NodeStyleService;
import com.codeprint.application.graph.WarningSuppressionService;
import com.codeprint.domain.graph.Edge;
import com.codeprint.domain.graph.Graph;
import com.codeprint.domain.graph.Node;
import com.codeprint.domain.user.User;
import com.codeprint.domain.user.UserRepository;
import com.codeprint.infrastructure.storage.S3Service;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.TimeUnit;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class GraphController {

    private final GraphQueryService graphQueryService;
    private final GraphCommandService graphCommandService;
    private final GraphFacade graphFacade;
    private final GraphDiffService graphDiffService;
    private final GraphWarningService graphWarningService;
    private final WarningSuppressionService warningSuppressionService;
    private final NodeStyleService nodeStyleService;
    private final UserRepository userRepository;
    private final S3Service s3Service;
    private final GraphResponseAssembler graphResponseAssembler;

    // 프로젝트의 그래프 버전 목록을 최신순으로 조회
    @GetMapping("/api/projects/{projectId}/graphs")
    public ResponseEntity<List<Map<String, Object>>> getGraphVersions(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(graphFacade.getGraphVersionsWithBranch(projectId, user.getId()));
    }

    // 그래프 버전을 고정 슬롯(1~5)에 고정 — 소유자만, 같은 슬롯 기존 고정은 덮어쓰기
    @PutMapping("/api/projects/{projectId}/graphs/{graphId}/pin")
    public ResponseEntity<Void> pinGraph(
            @PathVariable UUID projectId,
            @PathVariable UUID graphId,
            @Valid @RequestBody PinRequest request,
            @AuthenticationPrincipal User user) {
        graphFacade.verifyProjectOwnership(projectId, user.getId());
        graphCommandService.pinGraph(projectId, graphId, request.slot());
        return ResponseEntity.ok().build();
    }

    // 그래프 버전 고정 해제 — 소유자만
    @DeleteMapping("/api/projects/{projectId}/graphs/{graphId}/pin")
    public ResponseEntity<Void> unpinGraph(
            @PathVariable UUID projectId,
            @PathVariable UUID graphId,
            @AuthenticationPrincipal User user) {
        graphFacade.verifyProjectOwnership(projectId, user.getId());
        graphCommandService.unpinGraph(projectId, graphId);
        return ResponseEntity.ok().build();
    }

    // 고정 요청 — slot은 1~5 필수
    public record PinRequest(@NotNull @Min(1) @Max(5) Integer slot) {}

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

                    // 노드 스타일 맵 (nodeId → bgColor)
                    Map<String, String> styleMap = nodeStyleService.getStyles(graph.getId()).stream()
                            .collect(java.util.stream.Collectors.toMap(
                                    s -> s.getNodeId().toString(),
                                    s -> s.getBgColor() != null ? s.getBgColor() : ""
                            ));

                    List<Map<String, Object>> nodeData = nodes.stream()
                            .filter(n -> !n.isHidden())
                            .map(n -> graphResponseAssembler.toNodeDto(n, styleMap))
                            .toList();

                    List<Map<String, Object>> edgeData = edges.stream()
                            .filter(e -> !e.isHidden())
                            .map(graphResponseAssembler::toEdgeDto)
                            .toList();

                    // 소유자에게는 활성 경고와 숨긴 경고를 분리해 전달 — 새로고침 후에도 복원 가능하도록
                    Map<Boolean, List<Map<String, Object>>> partitioned =
                            partitionSuppressed(graph.getProjectId(), graphQueryService.getWarnings(graph.getId()));

                    // 경고 suppress/복원이 즉시 반영돼야 하므로 브라우저 캐시 미사용 (무거운 detect()는 서버 Caffeine 캐시가 담당)
                    Map<String, Object> body = new java.util.LinkedHashMap<>();
                    body.put("graphId", graph.getId().toString());
                    body.put("nodes", nodeData);
                    body.put("edges", edgeData);
                    body.put("warnings", partitioned.get(false));
                    body.put("suppressedWarnings", partitioned.get(true));
                    // 대형 레포 절단 안내 — 기존 그래프(NULL)는 미포함
                    if (graph.getTotalFileCount() != null) {
                        body.put("analyzedFileCount", graph.getAnalyzedFileCount());
                        body.put("totalFileCount", graph.getTotalFileCount());
                    }
                    return ResponseEntity.ok()
                            .cacheControl(CacheControl.noStore())
                            .body(body);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // 공개 프로젝트의 그래프를 비인증으로 조회 (오너 배경이미지 포함)
    @GetMapping("/api/share/{projectId}/graph")
    public ResponseEntity<?> getPublicGraph(@PathVariable UUID projectId) {
        var project = graphFacade.getPublicProject(projectId);
        String ownerBgUrl = userRepository.findById(project.getUserId())
                .map(u -> s3Service.toPresignedUrl(u.getGraphBgUrl()))
                .orElse(null);
        return graphQueryService.findLatestByProject(projectId)
                .map(graph -> {
                    List<Node> nodes = graphQueryService.getNodes(graph.getId());
                    List<Edge> edges = graphQueryService.getEdges(graph.getId());

                    List<Map<String, Object>> nodeData = nodes.stream()
                            .filter(n -> !n.isHidden())
                            .map(n -> graphResponseAssembler.toNodeDto(n, null))
                            .toList();

                    List<Map<String, Object>> edgeData = edges.stream()
                            .filter(e -> !e.isHidden())
                            .map(graphResponseAssembler::toEdgeDto)
                            .toList();

                    List<Map<String, Object>> warnings =
                            filterSuppressed(graph.getProjectId(), graphQueryService.getWarnings(graph.getId()));

                    // 공개 그래프는 누구나 접근 가능하므로 public 캐시 허용
                    Map<String, Object> body = new java.util.LinkedHashMap<>();
                    body.put("graphId", graph.getId().toString());
                    body.put("nodes", nodeData);
                    body.put("edges", edgeData);
                    body.put("warnings", warnings);
                    body.put("ownerBgUrl", ownerBgUrl);
                    // 대형 레포 절단 안내 — 기존 그래프(NULL)는 미포함
                    if (graph.getTotalFileCount() != null) {
                        body.put("analyzedFileCount", graph.getAnalyzedFileCount());
                        body.put("totalFileCount", graph.getTotalFileCount());
                    }
                    return ResponseEntity.ok()
                            .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic())
                            .body(body);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // 두 그래프 버전의 노드·엣지 변경 diff를 반환
    @GetMapping("/api/projects/{projectId}/diff")
    public ResponseEntity<?> getGraphDiff(
            @PathVariable UUID projectId,
            @RequestParam UUID from,
            @RequestParam UUID to,
            @AuthenticationPrincipal User user) {

        graphFacade.verifyProjectOwnership(projectId, user.getId());

        GraphDiffService.DiffResult result = graphDiffService.diff(from, to);

        List<Map<String, Object>> nodes = result.nodes().stream()
                .map(nd -> {
                    var n = nd.node();
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", n.getId().toString());
                    m.put("type", n.getType().name());
                    m.put("name", n.getName());
                    m.put("filePath", n.getFilePath() != null ? n.getFilePath() : "");
                    m.put("language", n.getLanguage() != null ? n.getLanguage() : "");
                    m.put("posX", n.getPosX());
                    m.put("posY", n.getPosY());
                    m.put("status", nd.status());
                    if (n.getMetadata() != null && n.getMetadata().containsKey("comment")) {
                        m.put("comment", n.getMetadata().get("comment"));
                    }
                    return m;
                }).toList();

        List<Map<String, Object>> edges = result.edges().stream()
                .map(ed -> {
                    var e = ed.edge();
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", e.getId().toString());
                    m.put("type", e.getType().name());
                    m.put("source", e.getSourceNodeId().toString());
                    m.put("target", e.getTargetNodeId().toString());
                    m.put("edgeIdentifier", e.getEdgeIdentifier());
                    m.put("status", ed.status());
                    m.put("sourceName", ed.sourceName());
                    m.put("targetName", ed.targetName());
                    return m;
                }).toList();

        long added = nodes.stream().filter(n -> "added".equals(n.get("status"))).count();
        long removed = nodes.stream().filter(n -> "removed".equals(n.get("status"))).count();
        long unchanged = nodes.stream().filter(n -> "unchanged".equals(n.get("status"))).count();

        return ResponseEntity.ok(Map.of(
                "nodes", nodes,
                "edges", edges,
                "summary", Map.of("added", added, "removed", removed, "unchanged", unchanged)
        ));
    }

    // 노드 사용자 정의 레이블/메모 저장 — 그래프 소유자만 가능
    @PutMapping("/api/graphs/{graphId}/nodes/{nodeId}/annotation")
    public ResponseEntity<Void> updateNodeAnnotation(
            @PathVariable UUID graphId,
            @PathVariable UUID nodeId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal User user) {

        graphFacade.verifyGraphOwnership(graphId, user.getId());

        graphCommandService.updateNodeAnnotation(nodeId, body.get("userLabel"), body.get("userNote"));
        return ResponseEntity.ok().build();
    }

    // 노드 드래그 후 위치를 저장 — 그래프 소유자만 가능
    @PutMapping("/api/graphs/{graphId}/nodes/{nodeId}/position")
    public ResponseEntity<Void> updateNodePosition(
            @PathVariable UUID graphId,
            @PathVariable UUID nodeId,
            @RequestBody Map<String, Double> body,
            @AuthenticationPrincipal User user) {

        graphFacade.verifyGraphOwnership(graphId, user.getId());

        double x = body.getOrDefault("x", 0.0);
        double y = body.getOrDefault("y", 0.0);
        graphCommandService.updateNodePosition(nodeId, x, y);
        return ResponseEntity.ok().build();
    }

    // 프로젝트에서 suppress(숨김)된 fingerprint의 경고를 제외하고 반환
    private List<Map<String, Object>> filterSuppressed(UUID projectId, List<Map<String, Object>> warnings) {
        return partitionSuppressed(projectId, warnings).get(false);
    }

    // 경고를 suppress 여부로 분리 — true=숨긴 경고, false=활성 경고
    private Map<Boolean, List<Map<String, Object>>> partitionSuppressed(UUID projectId, List<Map<String, Object>> warnings) {
        Set<String> suppressed = warningSuppressionService.getSuppressedFingerprints(projectId);
        return warnings.stream()
                .collect(java.util.stream.Collectors.partitioningBy(w -> suppressed.contains(w.get("fingerprint"))));
    }
}
