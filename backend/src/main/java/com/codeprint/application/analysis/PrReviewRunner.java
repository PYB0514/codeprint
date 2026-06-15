// PR 리뷰를 비동기로 실행하는 빈 — webhook이 GitHub 타임아웃 없이 빠르게 응답하도록 분리
package com.codeprint.application.analysis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PrReviewRunner {

    private final PrReviewService prReviewService;

    // PR 리뷰를 비동기 실행 — 실패해도 webhook 응답에 영향 없도록 로깅만
    @Async
    public void reviewAsync(UUID projectId, int prNumber, UUID ownerId, String githubToken) {
        try {
            prReviewService.review(projectId, prNumber, ownerId, githubToken);
        } catch (Exception e) {
            log.error("webhook PR 리뷰 비동기 실행 실패: project={}, pr={}", projectId, prNumber, e);
        }
    }
}
