// StuckAnalysisCleaner 단위 테스트 — 재기동 시 RUNNING/PENDING→FAILED 전이·빈목록 경계
package com.codeprint.application.analysis;

import com.codeprint.domain.analysis.AnalysisRepository;
import com.codeprint.domain.analysis.AnalysisResult;
import com.codeprint.domain.analysis.AnalysisStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StuckAnalysisCleanerTest {

    @Mock
    private AnalysisRepository analysisRepository;

    private StuckAnalysisCleaner cleaner() {
        return new StuckAnalysisCleaner(analysisRepository);
    }

    // RUNNING 상태로 시작된 분석 결과를 만든다
    private AnalysisResult running() {
        AnalysisResult a = AnalysisResult.create(UUID.randomUUID(), "main");
        a.start();
        return a;
    }

    @Test
    @DisplayName("RUNNING/PENDING 분석을 FAILED로 전이하고 각각 저장한다")
    void cleanup_stuck_분석을_FAILED로() {
        AnalysisResult pending = AnalysisResult.create(UUID.randomUUID(), "main");
        AnalysisResult started = running();
        when(analysisRepository.findByStatusIn(List.of(AnalysisStatus.PENDING, AnalysisStatus.RUNNING)))
                .thenReturn(List.of(pending, started));

        cleaner().cleanupStuckAnalyses();

        assertThat(pending.getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(started.getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(pending.getErrorMsg()).contains("서버 재시작");
        verify(analysisRepository, times(2)).save(any(AnalysisResult.class));
    }

    @Test
    @DisplayName("stuck 분석이 없으면 아무것도 저장하지 않는다")
    void cleanup_빈목록_경계() {
        when(analysisRepository.findByStatusIn(List.of(AnalysisStatus.PENDING, AnalysisStatus.RUNNING)))
                .thenReturn(List.of());

        cleaner().cleanupStuckAnalyses();

        verify(analysisRepository, never()).save(any(AnalysisResult.class));
    }
}
