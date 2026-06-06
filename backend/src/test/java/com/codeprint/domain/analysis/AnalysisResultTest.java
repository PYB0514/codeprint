// AnalysisResult 상태 전이 단위 테스트 — 분석 생명주기 회귀 방지
package com.codeprint.domain.analysis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisResultTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final String BRANCH = "main";

    @Test
    @DisplayName("create() — PENDING 상태, progress=0, branch 설정")
    void create_initialState() {
        AnalysisResult result = AnalysisResult.create(PROJECT_ID, BRANCH);

        assertThat(result.getStatus()).isEqualTo(AnalysisStatus.PENDING);
        assertThat(result.getProgress()).isEqualTo(0);
        assertThat(result.getBranch()).isEqualTo(BRANCH);
        assertThat(result.getProjectId()).isEqualTo(PROJECT_ID);
        assertThat(result.getId()).isNotNull();
        assertThat(result.getCreatedAt()).isNotNull();
        assertThat(result.getErrorMsg()).isNull();
        assertThat(result.getStartedAt()).isNull();
        assertThat(result.getFinishedAt()).isNull();
    }

    @Test
    @DisplayName("start() — RUNNING 상태, startedAt 기록")
    void start_transitionToRunning() {
        AnalysisResult result = AnalysisResult.create(PROJECT_ID, BRANCH);

        result.start();

        assertThat(result.getStatus()).isEqualTo(AnalysisStatus.RUNNING);
        assertThat(result.getStartedAt()).isNotNull();
        assertThat(result.getFinishedAt()).isNull();
    }

    @Test
    @DisplayName("complete() — DONE 상태, progress=100, commitSha 저장")
    void complete_transitionToDone() {
        AnalysisResult result = AnalysisResult.create(PROJECT_ID, BRANCH);
        result.start();
        String sha = "abc123def456";

        result.complete(sha);

        assertThat(result.getStatus()).isEqualTo(AnalysisStatus.DONE);
        assertThat(result.getProgress()).isEqualTo(100);
        assertThat(result.getLastCommitSha()).isEqualTo(sha);
        assertThat(result.getFinishedAt()).isNotNull();
    }

    @Test
    @DisplayName("fail() — FAILED 상태, errorMsg 저장, finishedAt 기록")
    void fail_transitionToFailed() {
        AnalysisResult result = AnalysisResult.create(PROJECT_ID, BRANCH);
        result.start();
        String errorMsg = "clone failed: repository not found";

        result.fail(errorMsg);

        assertThat(result.getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(result.getErrorMsg()).isEqualTo(errorMsg);
        assertThat(result.getFinishedAt()).isNotNull();
        assertThat(result.getProgress()).isEqualTo(0); // 실패 시 progress 그대로
    }

    @Test
    @DisplayName("updateProgress() — 0~100 범위 클램핑")
    void updateProgress_clampsToRange() {
        AnalysisResult result = AnalysisResult.create(PROJECT_ID, BRANCH);
        result.start();

        result.updateProgress(50);
        assertThat(result.getProgress()).isEqualTo(50);

        result.updateProgress(150); // 100 초과 → 100으로 클램핑
        assertThat(result.getProgress()).isEqualTo(100);

        result.updateProgress(-10); // 음수 → 0으로 클램핑
        assertThat(result.getProgress()).isEqualTo(0);
    }

    @Test
    @DisplayName("PENDING → RUNNING → DONE 전체 생명주기")
    void fullLifecycle_pendingToRunningToDone() {
        AnalysisResult result = AnalysisResult.create(PROJECT_ID, BRANCH);
        assertThat(result.getStatus()).isEqualTo(AnalysisStatus.PENDING);

        result.start();
        assertThat(result.getStatus()).isEqualTo(AnalysisStatus.RUNNING);

        result.updateProgress(60);
        assertThat(result.getProgress()).isEqualTo(60);

        result.complete("deadbeef");
        assertThat(result.getStatus()).isEqualTo(AnalysisStatus.DONE);
        assertThat(result.getProgress()).isEqualTo(100);
    }

    @Test
    @DisplayName("PENDING → RUNNING → FAILED 실패 생명주기")
    void fullLifecycle_pendingToRunningToFailed() {
        AnalysisResult result = AnalysisResult.create(PROJECT_ID, BRANCH);

        result.start();
        result.updateProgress(30);
        result.fail("out of memory");

        assertThat(result.getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(result.getErrorMsg()).isEqualTo("out of memory");
        assertThat(result.getFinishedAt()).isNotNull();
        assertThat(result.getStartedAt()).isNotNull();
    }
}
