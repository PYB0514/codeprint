// 프로젝트 경고 suppress(숨김)/해제 + 오탐 신고 REST API
package com.codeprint.interfaces.api;

import com.codeprint.application.graph.FpReportService;
import com.codeprint.application.graph.GraphFacade;
import com.codeprint.application.graph.WarningSuppressionService;
import com.codeprint.domain.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/warnings")
@RequiredArgsConstructor
public class WarningController {

    private final WarningSuppressionService warningSuppressionService;
    private final FpReportService fpReportService;
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

    // 오탐 신고 — 프로젝트를 읽을 수 있는 사용자면 누구나(소유자 아니어도 가능, 숨기기와의 차이점)
    @PostMapping("/report-fp")
    public ResponseEntity<Void> reportFalsePositive(
            @PathVariable UUID projectId,
            @RequestBody ReportFpRequest req,
            @AuthenticationPrincipal User user) {
        if (req.fingerprint() == null || req.fingerprint().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        graphFacade.verifyProjectReadAccess(projectId, user.getId());
        fpReportService.reportFalsePositive(projectId, req.fingerprint(), req.type(), user.getId(), req.reason());
        return ResponseEntity.noContent().build();
    }

    // 내가 신고한 fingerprint 목록 — 버튼 상태(신고됨) 표시용
    @GetMapping("/report-fp/mine")
    public ResponseEntity<Set<String>> myReportedFingerprints(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal User user) {
        graphFacade.verifyProjectReadAccess(projectId, user.getId());
        return ResponseEntity.ok(fpReportService.getReportedFingerprintsByUser(projectId, user.getId()));
    }

    // 오탐 신고 요청 — reason은 선택 입력
    record ReportFpRequest(String fingerprint, String type, String reason) {}
}
