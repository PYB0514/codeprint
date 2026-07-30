// FeaturedAnalysisTriggerAdapter 단위 테스트 — 동시성 슬롯 예약/반납 회귀 방지
// (2026-07-30 적대적 검증 CONFIRMED — 이 어댑터가 AnalysisApplicationService를 거치지 않고
// AnalysisRunner.run()을 직접 호출하면서도 슬롯 예약이 전혀 없어, AnalysisRunner의 finally release()가
// 매번 과다 반납(over-release)돼 세마포어 상한이 영구히 깨지는 회귀가 있었다. 테스트 부재가 이 결함이
// 놓친 원인이었다는 것도 지적됨 — 이 파일이 그 공백을 메운다.)
package com.codeprint.infrastructure.adapter;

import com.codeprint.application.analysis.AnalysisRunner;
import com.codeprint.domain.analysis.AnalysisRepository;
import com.codeprint.infrastructure.config.AnalysisConcurrencyGuard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeaturedAnalysisTriggerAdapterTest {

    @Mock private AnalysisRepository analysisRepository;
    @Mock private AnalysisRunner analysisRunner;
    @Mock private AnalysisConcurrencyGuard concurrencyGuard;

    private FeaturedAnalysisTriggerAdapter adapter() {
        return new FeaturedAnalysisTriggerAdapter(analysisRepository, analysisRunner, concurrencyGuard);
    }

    @Test
    @DisplayName("슬롯 예약에 실패하면(포화) 분석 레코드조차 생성하지 않고 스킵한다")
    void 포화시_스킵() {
        UUID projectId = UUID.randomUUID();
        when(concurrencyGuard.tryAcquire()).thenReturn(false);

        adapter().triggerAnalysis(projectId, "https://github.com/a/b");

        verifyNoInteractions(analysisRepository);
        verifyNoInteractions(analysisRunner);
        verify(concurrencyGuard, never()).release();
    }

    @Test
    @DisplayName("정상 제출 시 슬롯을 반납하지 않는다(소유권이 AnalysisRunner로 이전)")
    void 정상제출_슬롯유지() {
        UUID projectId = UUID.randomUUID();
        when(concurrencyGuard.tryAcquire()).thenReturn(true);

        adapter().triggerAnalysis(projectId, "https://github.com/a/b");

        verify(analysisRepository).save(any());
        verify(analysisRunner).run(any(), eq(projectId), eq("https://github.com/a/b"), eq((String) null), eq((String) null), eq((String) null));
        verify(concurrencyGuard, never()).release();
    }

    @Test
    @DisplayName("제출 자체가 실패하면 예약했던 슬롯을 반납한다")
    void 제출실패시_슬롯반납() {
        UUID projectId = UUID.randomUUID();
        when(concurrencyGuard.tryAcquire()).thenReturn(true);
        org.mockito.Mockito.doThrow(new RuntimeException("boom"))
                .when(analysisRunner).run(any(), any(), any(), any(), any(), any());

        assertThatThrownBy(() -> adapter().triggerAnalysis(projectId, "https://github.com/a/b"))
                .isInstanceOf(RuntimeException.class);

        verify(concurrencyGuard).release();
    }
}
