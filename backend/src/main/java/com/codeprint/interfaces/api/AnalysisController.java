// 코드 분석 시작 및 조회 REST API 컨트롤러
package com.codeprint.interfaces.api;

import com.codeprint.application.analysis.AnalysisFacade;
import com.codeprint.domain.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/analyses")
@RequiredArgsConstructor
public class AnalysisController {

    private final AnalysisFacade analysisFacade;

    // 프로젝트 소유권 확인 후 코드 분석을 시작하고 analysisId를 반환
    @PostMapping
    public ResponseEntity<Map<String, Object>> startAnalysis(
            @RequestBody StartAnalysisRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.status(202).body(
                analysisFacade.startAnalysis(request.projectId(), request.branch(),
                        user.getId(), user.getGithubAccessToken()));
    }

    // 분석 소유권 확인 후 상태 및 진행률 조회
    @GetMapping("/{analysisId}")
    public ResponseEntity<Map<String, Object>> getAnalysis(
            @PathVariable UUID analysisId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(analysisFacade.getAnalysis(analysisId, user.getId()));
    }

    // 분석 시작 요청 DTO (프로젝트 ID + 브랜치명)
    public record StartAnalysisRequest(UUID projectId, String branch) {}
}
