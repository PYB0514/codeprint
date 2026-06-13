// 프로젝트 경고 suppress(숨김)/해제 REST API — 프로젝트 소유자만 가능
package com.codeprint.interfaces.api;

import com.codeprint.application.graph.GraphFacade;
import com.codeprint.application.graph.WarningSuppressionService;
import com.codeprint.domain.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/warnings")
@RequiredArgsConstructor
public class WarningController {

    private final WarningSuppressionService warningSuppressionService;
    private final GraphFacade graphFacade;

    // 경고 suppress(숨김) — 프로젝트 소유자만
    @PostMapping("/suppress")
    public ResponseEntity<Void> suppress(
            @PathVariable UUID projectId,
            @RequestBody SuppressRequest req,
            @AuthenticationPrincipal User user) {
        if (req.fingerprint() == null || req.fingerprint().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        graphFacade.verifyProjectOwnership(projectId, user.getId());
        warningSuppressionService.suppress(projectId, req.fingerprint(), req.type());
        return ResponseEntity.noContent().build();
    }

    // 경고 suppress 해제 — 프로젝트 소유자만
    @DeleteMapping("/suppress/{fingerprint}")
    public ResponseEntity<Void> unsuppress(
            @PathVariable UUID projectId,
            @PathVariable String fingerprint,
            @AuthenticationPrincipal User user) {
        graphFacade.verifyProjectOwnership(projectId, user.getId());
        warningSuppressionService.unsuppress(projectId, fingerprint);
        return ResponseEntity.noContent().build();
    }

    // suppress 요청 — fingerprint는 경고 응답의 fingerprint 필드, type은 표시용(선택)
    record SuppressRequest(String fingerprint, String type) {}
}
