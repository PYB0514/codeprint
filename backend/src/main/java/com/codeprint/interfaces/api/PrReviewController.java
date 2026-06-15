// PR 리뷰 REST API — PR head 브랜치를 분석해 구조 경고를 PR 코멘트로 게시 (프로젝트 소유자만)
package com.codeprint.interfaces.api;

import com.codeprint.application.analysis.PrReviewService;
import com.codeprint.domain.user.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/pr-review")
@RequiredArgsConstructor
public class PrReviewController {

    private final PrReviewService prReviewService;

    // PR 리뷰 실행 — 소유자만, PR head 분석 후 경고를 PR 코멘트로 게시
    @PostMapping
    public ResponseEntity<Map<String, Object>> review(
            @PathVariable UUID projectId,
            @Valid @RequestBody PrReviewRequest request,
            @AuthenticationPrincipal User user) {
        Map<String, Object> result = prReviewService.review(
                projectId, request.prNumber(), user.getId(), user.getGithubAccessToken());
        return ResponseEntity.ok(result);
    }

    // PR 리뷰 요청 — prNumber는 양의 정수 필수
    record PrReviewRequest(@NotNull @Positive Integer prNumber) {}
}
