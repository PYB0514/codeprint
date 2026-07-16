// StaleAnalysisReconciliationService 단위 테스트 — 스테일 판정 시간창·재처리 분기 회귀 방지(안정성 갭 C+F)
package com.codeprint.application.analysis;

import com.codeprint.domain.analysis.AnalysisRepository;
import com.codeprint.domain.analysis.AnalysisResult;
import com.codeprint.domain.analysis.AnalysisStatus;
import com.codeprint.interfaces.websocket.AnalysisProgressHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StaleAnalysisReconciliationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-17T12:00:00Z");

    private AnalysisRepository analysisRepository;
    private AnalysisProgressHandler progressHandler;
    private StaleAnalysisReconciliationService service;

    private void setUp() {
        analysisRepository = mock(AnalysisRepository.class);
        progressHandler = mock(AnalysisProgressHandler.class);
        service = new StaleAnalysisReconciliationService(analysisRepository, progressHandler);
    }

    @Test
    @DisplayName("isStale — 상한(20분) 초과면 스테일")
    void isStale_pastThreshold_true() {
        assertThat(StaleAnalysisReconciliationService.isStale(
                NOW.minusSeconds(21 * 60), NOW)).isTrue();
    }

    @Test
    @DisplayName("isStale — 상한 이내면 스테일 아님")
    void isStale_withinThreshold_false() {
        assertThat(StaleAnalysisReconciliationService.isStale(
                NOW.minusSeconds(5 * 60), NOW)).isFalse();
    }

    @Test
    @DisplayName("20분 넘게 RUNNING인 분석은 FAILED로 전환하고 진행률 알림을 보낸다")
    void reconcile_staleRunning_marksFailedAndNotifies() {
        setUp();
        UUID id = UUID.randomUUID();
        AnalysisResult analysis = AnalysisResult.create(UUID.randomUUID(), "main");
        analysis.start();
        setStartedAt(analysis, Instant.now().minusSeconds(25 * 60));
        when(analysisRepository.findByStatusIn(List.of(AnalysisStatus.RUNNING))).thenReturn(List.of(analysis));

        int reconciled = service.reconcile();

        assertThat(reconciled).isEqualTo(1);
        assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.FAILED);
        verify(analysisRepository).save(analysis);
        verify(progressHandler).sendProgress(analysis.getId(), 0, "FAILED");
    }

    @Test
    @DisplayName("20분 이내 RUNNING인 분석은 건드리지 않는다")
    void reconcile_recentRunning_untouched() {
        setUp();
        AnalysisResult analysis = AnalysisResult.create(UUID.randomUUID(), "main");
        analysis.start();
        setStartedAt(analysis, Instant.now().minusSeconds(60));
        when(analysisRepository.findByStatusIn(List.of(AnalysisStatus.RUNNING))).thenReturn(List.of(analysis));

        int reconciled = service.reconcile();

        assertThat(reconciled).isEqualTo(0);
        assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.RUNNING);
        verify(analysisRepository, never()).save(any());
        verify(progressHandler, never()).sendProgress(any(), anyInt(), anyString());
    }

    // startedAt을 강제 조작 — AnalysisResult에 테스트 전용 setter를 추가하지 않기 위함
    private void setStartedAt(AnalysisResult analysis, Instant startedAt) {
        ReflectionTestUtils.setField(analysis, "startedAt", startedAt);
    }
}
