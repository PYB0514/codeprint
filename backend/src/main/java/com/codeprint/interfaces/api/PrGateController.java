// PR 게이트 셀프서비스 연결 REST API — 소유자가 프로젝트별 webhook 시크릿을 발급/조회/재발급/해제
package com.codeprint.interfaces.api;

import com.codeprint.application.analysis.AnalysisFacade;
import com.codeprint.application.analysis.PrGateStatus;
import com.codeprint.domain.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/pr-gate")
@RequiredArgsConstructor
public class PrGateController {

    private final AnalysisFacade analysisFacade;

    // 연결 상태 조회 — 미연결이면 secret null, connected false
    @GetMapping
    public ResponseEntity<PrGateStatus> getStatus(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(analysisFacade.getPrGateStatus(projectId, user.getId()));
    }

    // 최초 연결 — 시크릿이 없을 때만 신규 발급(있으면 기존 값 유지)
    @PostMapping("/connect")
    public ResponseEntity<PrGateStatus> connect(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(analysisFacade.connectPrGate(projectId, user.getId()));
    }

    // 시크릿 재발급 — 기존 시크릿은 즉시 무효화
    @PostMapping("/rotate")
    public ResponseEntity<PrGateStatus> rotate(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(analysisFacade.rotatePrGateSecret(projectId, user.getId()));
    }

    // 연결 해제 — 시크릿 제거
    @DeleteMapping
    public ResponseEntity<PrGateStatus> disconnect(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(analysisFacade.disconnectPrGate(projectId, user.getId()));
    }
}
