// 노드 스타일 REST API — 배경색 등 커스텀 시각화 속성 저장/조회
package com.codeprint.interfaces.api;

import com.codeprint.application.graph.GraphFacade;
import com.codeprint.application.graph.NodeStyleService;
import com.codeprint.domain.user.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
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
    private final GraphFacade graphFacade;

    // 노드 배경색 설정 — bgColor null/빈 문자열이면 초기화. 프론트가 실제로 보내는 값은 항상 고정 팔레트의 #RRGGBB 헥스뿐이라 그 형식만 허용
    @PutMapping("/api/graphs/{graphId}/nodes/{nodeId}/style")
    public ResponseEntity<Map<String, Object>> upsertStyle(
            @PathVariable UUID graphId,
            @PathVariable UUID nodeId,
            @Valid @RequestBody UpsertStyleRequest request,
            @AuthenticationPrincipal User user) {

        verifyOwnership(graphId, user);

        String bgColor = request.bgColor();
        if (bgColor == null || bgColor.isBlank()) {
            nodeStyleService.clearStyle(graphId, nodeId);
            return ResponseEntity.ok(Map.of("bgColor", ""));
        }

        var style = nodeStyleService.upsertStyle(graphId, nodeId, bgColor.trim());
        return ResponseEntity.ok(Map.of("bgColor", style.getBgColor()));
    }

    // 그래프 → 프로젝트 소유권 검증
    private void verifyOwnership(UUID graphId, User user) {
        graphFacade.verifyGraphOwnership(graphId, user.getId());
    }

    // 노드 배경색 설정 요청 DTO — bgColor는 없거나 빈 문자열(초기화) 또는 #RRGGBB 헥스 형식만 허용(DB bg_color 컬럼 length=20과 정합)
    record UpsertStyleRequest(@Pattern(regexp = "^(#[0-9a-fA-F]{6})?$") String bgColor) {}
}
