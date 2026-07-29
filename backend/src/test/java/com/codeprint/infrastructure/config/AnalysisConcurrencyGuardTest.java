// AnalysisConcurrencyGuard 단위 테스트 — 공유 taskExecutor 부하에 따른 한도 판정 검증
package com.codeprint.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalysisConcurrencyGuardTest {

    @Mock
    private ThreadPoolTaskExecutor taskExecutor;
    @Mock
    private ThreadPoolExecutor delegate;
    @Mock
    private BlockingQueue<Runnable> queue;

    private AnalysisConcurrencyGuard guard() {
        return new AnalysisConcurrencyGuard(taskExecutor);
    }

    @Test
    @DisplayName("활성+대기 작업 합이 한도 미만이면 여유 있음")
    void 한도_미만이면_여유() {
        when(taskExecutor.getActiveCount()).thenReturn(3);
        when(taskExecutor.getThreadPoolExecutor()).thenReturn(delegate);
        when(delegate.getQueue()).thenReturn(queue);
        when(queue.size()).thenReturn(10);

        assertThat(guard().isAtCapacity()).isFalse();
    }

    @Test
    @DisplayName("활성+대기 작업 합이 한도 이상이면 포화 상태")
    void 한도_이상이면_포화() {
        when(taskExecutor.getActiveCount()).thenReturn(8);
        when(taskExecutor.getThreadPoolExecutor()).thenReturn(delegate);
        when(delegate.getQueue()).thenReturn(queue);
        when(queue.size()).thenReturn(32);

        assertThat(guard().isAtCapacity()).isTrue();
    }

    @Test
    @DisplayName("경계값(정확히 한도)도 포화로 판정한다")
    void 경계값_포화() {
        when(taskExecutor.getActiveCount()).thenReturn(0);
        when(taskExecutor.getThreadPoolExecutor()).thenReturn(delegate);
        when(delegate.getQueue()).thenReturn(queue);
        when(queue.size()).thenReturn(40);

        assertThat(guard().isAtCapacity()).isTrue();
    }
}
