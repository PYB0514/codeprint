// G-5 웹훅 유실 안전망 — 연결된 프로젝트의 열린 PR 중 codeprint/structure 상태가 없는 것을 찾아 리뷰를 재트리거
package com.codeprint.application.analysis;

import com.codeprint.infrastructure.github.GitHubApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class PrGateReconciliationService {

    // 정상 webhook 배달·비동기 리뷰가 끝날 유예 시간 — 이보다 최근에 push된 PR은 아직 처리 중일 수 있어 건너뜀
    static final Duration GRACE_PERIOD = Duration.ofMinutes(10);
    // 리컨실리에이션 대상 상한 — 이보다 오래된 PR은 이미 다른 경로(close→reopen 등)로 해소됐다고 보고 재시도하지 않음
    static final Duration MAX_AGE = Duration.ofHours(24);

    private final AnalysisFacade analysisFacade;
    private final GitHubApiClient gitHubApiClient;
    private final PrReviewRunner prReviewRunner;

    // PR의 마지막 갱신 시각이 재트리거 대상 시간창(GRACE_PERIOD ~ MAX_AGE 전) 안에 있는지 — 순수 함수(단위 테스트 대상)
    static boolean withinReconcileWindow(Instant updatedAt, Instant now) {
        Duration age = Duration.between(updatedAt, now);
        return age.compareTo(GRACE_PERIOD) >= 0 && age.compareTo(MAX_AGE) <= 0;
    }

    // 연결된 프로젝트 전체를 순회하며 유실된 webhook을 찾아 리뷰 재트리거 — 재트리거한 PR 수 반환
    public int reconcile() {
        Instant now = Instant.now();
        int triggered = 0;
        for (PrGateConnectedProject project : analysisFacade.listPrGateConnectedProjects()) {
            triggered += reconcileProject(project, now);
        }
        return triggered;
    }

    // 프로젝트 하나의 열린 PR을 검사 — 한 프로젝트의 GitHub API 실패가 전체 순회를 막지 않도록 격리
    private int reconcileProject(PrGateConnectedProject project, Instant now) {
        try {
            int triggered = 0;
            for (var pr : gitHubApiClient.fetchOpenPullRequests(project.repoUrl(), project.githubToken())) {
                if (!withinReconcileWindow(pr.updatedAt(), now)) continue;
                if (gitHubApiClient.hasStructureCommitStatus(project.repoUrl(), pr.headSha(), project.githubToken())) continue;
                log.info("G-5 리컨실리에이션 — 유실 감지, 리뷰 재트리거: repo={}, pr={}", project.repoUrl(), pr.number());
                prReviewRunner.reviewAsync(project.projectId(), pr.number(), project.ownerId(), project.githubToken());
                triggered++;
            }
            return triggered;
        } catch (Exception e) {
            log.warn("G-5 리컨실리에이션 — 프로젝트 처리 실패(다음 프로젝트 계속): repo={}", project.repoUrl(), e);
            return 0;
        }
    }
}
