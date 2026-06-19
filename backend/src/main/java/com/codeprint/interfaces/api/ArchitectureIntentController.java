// 프로젝트 의도 아키텍처 REST API — GET/PUT /api/projects/{projectId}/architecture-intent
package com.codeprint.interfaces.api;

import com.codeprint.application.graph.ArchitectureIntentService;
import com.codeprint.application.graph.GraphFacade;
import com.codeprint.domain.graph.ArchitectureIntent;
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

    // 프로젝트의 의도 아키텍처 저장 — 소유자만
    @PutMapping("/api/projects/{projectId}/architecture-intent")
    public ResponseEntity<Void> saveIntent(
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
                        .toList()
        );
        intentService.save(projectId, intent);
        return ResponseEntity.ok().build();
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
                        .toList()
        );
    }

    // 요청 DTOs
    public record IntentRequest(
            @NotNull List<@Valid ModuleDto> modules,
            @NotNull List<@Valid RuleDto> rules) {}

    public record ModuleDto(
            @NotBlank String name,
            @NotNull List<String> globs) {}

    public record RuleDto(
            @NotBlank String from,
            @NotBlank String to) {}
}
