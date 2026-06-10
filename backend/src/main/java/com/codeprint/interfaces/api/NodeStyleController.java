// 노드 스타일 REST API — 배경색 등 커스텀 시각화 속성 저장/조회
package com.codeprint.interfaces.api;

import com.codeprint.application.graph.GraphQueryService;
import com.codeprint.application.graph.NodeStyleService;
import com.codeprint.application.project.ProjectQueryService;
import com.codeprint.domain.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class NodeStyleController {

    private final NodeStyleService nodeStyleService;
    private final GraphQueryService graphQueryService;
    private final ProjectQueryService projectQueryService;

    // 노드 배경색 설정 — bgColor null 이면 초기화
    @PutMapping("/api/graphs/{graphId}/nodes/{nodeId}/style")
    public ResponseEntity<Map<String, Object>> upsertStyle(
            @PathVariable UUID graphId,
            @PathVariable UUID nodeId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal User user) {

        verifyOwnership(graphId, user);

        String bgColor = body.get("bgColor");
        if (bgColor == null || bgColor.isBlank()) {
            nodeStyleService.clearStyle(graphId, nodeId);
            return ResponseEntity.ok(Map.of("bgColor", ""));
        }

        var style = nodeStyleService.upsertStyle(graphId, nodeId, bgColor.trim());
        return ResponseEntity.ok(Map.of("bgColor", style.getBgColor()));
    }

    // 그래프 → 프로젝트 소유권 검증
    private void verifyOwnership(UUID graphId, User user) {
        graphQueryService.findById(graphId)
                .ifPresent(graph -> projectQueryService.getProject(graph.getProjectId(), user.getId()));
    }
}
