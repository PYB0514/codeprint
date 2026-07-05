// 프로젝트 의도 아키텍처 REST API — GET/PUT /api/projects/{projectId}/architecture-intent
package com.codeprint.interfaces.api;

import com.codeprint.application.graph.ArchitectureIntentService;
import com.codeprint.application.graph.GraphFacade;
import com.codeprint.application.graph.GraphQueryService;
import com.codeprint.application.graph.GraphWarningService;
import com.codeprint.domain.graph.ArchitectureIntent;
import com.codeprint.domain.graph.Edge;
import com.codeprint.domain.graph.Node;
import com.codeprint.domain.user.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ArchitectureIntentController {

    private final ArchitectureIntentService intentService;
    private final GraphFacade graphFacade;
    private final GraphQueryService graphQueryService;
    private final GraphWarningService graphWarningService;

    // 프로젝트의 의도 아키텍처 조회 — 소유자만
    @GetMapping("/api/projects/{projectId}/architecture-intent")
    public ResponseEntity<?> getIntent(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal User user) {
        graphFacade.verifyProjectOwnership(projectId, user.getId());
        return intentService.findByProjectId(projectId)
                .<ResponseEntity<?>>map(intent -> ResponseEntity.ok(toResponse(intent)))
                .orElse(ResponseEntity.notFound().build());
    }

    // 프로젝트의 의도 아키텍처 저장 — 소유자만. 저장 직후 현재 위반 수를 함께 반환(A4)
    @PutMapping("/api/projects/{projectId}/architecture-intent")
    public ResponseEntity<Map<String, Object>> saveIntent(
            @PathVariable UUID projectId,
            @Valid @RequestBody IntentRequest request,
            @AuthenticationPrincipal User user) {
        graphFacade.verifyProjectOwnership(projectId, user.getId());
        ArchitectureIntent intent = new ArchitectureIntent(
                request.modules().stream()
                        .map(m -> new ArchitectureIntent.Module(m.name(), m.globs()))
                        .toList(),
                request.rules().stream()
                        .map(r -> new ArchitectureIntent.DependencyRule(r.from(), r.to()))
                        .toList(),
                // ignore 미지정(구버전 클라이언트)이면 빈 목록 — 기존 동작 보존
                (request.ignore() == null ? List.<IgnoreDto>of() : request.ignore()).stream()
                        .map(g -> new ArchitectureIntent.IgnoreRule(g.type(), g.from(), g.to()))
                        .toList()
        );
        intentService.save(projectId, intent);
        return ResponseEntity.ok(Map.of("violationCount", countIntentDriftViolations(projectId, intent)));
    }

    // 방금 저장한 의도 규칙 기준으로 최신 그래프의 INTENT_DRIFT 위반 수를 계산 — 그래프가 없으면 0
    private long countIntentDriftViolations(UUID projectId, ArchitectureIntent intent) {
        return graphQueryService.findLatestByProject(projectId)
                .map(graph -> {
                    List<Node> nodes = graphQueryService.getNodes(graph.getId());
                    List<Edge> edges = graphQueryService.getEdges(graph.getId());
                    return graphWarningService.detect(nodes, edges, intent).stream()
                            .filter(w -> "INTENT_DRIFT".equals(w.get("type")))
                            .count();
                })
                .orElse(0L);
    }

    // 프로젝트의 의도 아키텍처 삭제 — 소유자만
    @DeleteMapping("/api/projects/{projectId}/architecture-intent")
    public ResponseEntity<Void> deleteIntent(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal User user) {
        graphFacade.verifyProjectOwnership(projectId, user.getId());
        intentService.delete(projectId);
        return ResponseEntity.ok().build();
    }

    // ArchitectureIntent → 응답 Map 변환
    private Map<String, Object> toResponse(ArchitectureIntent intent) {
        return Map.of(
                "modules", intent.modules().stream()
                        .map(m -> Map.of("name", m.name(), "globs", m.globs()))
                        .toList(),
                "rules", intent.rules().stream()
                        .map(r -> Map.of("from", r.from(), "to", r.to()))
                        .toList(),
                "ignore", intent.ignores().stream()
                        .map(g -> Map.of(
                                "type", g.type() == null ? "" : g.type(),
                                "from", g.fromGlob() == null ? "" : g.fromGlob(),
                                "to", g.toGlob() == null ? "" : g.toGlob()))
                        .toList()
        );
    }

    // 요청 DTOs — ignore는 선택(미지정 시 기존 동작 유지)
    public record IntentRequest(
            @NotNull List<@Valid ModuleDto> modules,
            @NotNull List<@Valid RuleDto> rules,
            List<@Valid IgnoreDto> ignore) {}

    public record ModuleDto(
            @NotBlank String name,
            @NotNull List<String> globs) {}

    public record RuleDto(
            @NotBlank String from,
            @NotBlank String to) {}

    // 경고 예외 규칙 DTO — 세 필드 모두 선택(빈 값=와일드카드). 단 전부 비면 모든 경고를 억제하므로 최소 하나는 의미값 권장.
    public record IgnoreDto(String type, String from, String to) {}
}
