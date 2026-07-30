// AnalysisConcurrencyGuard 단위 테스트 — 세마포어 기반 원자적 슬롯 예약/반납 검증
// (2026-07-30 적대적 검증에서 TOCTOU 레이스 CONFIRMED — 통계 읽기 방식에서 세마포어 방식으로 재설계)
package com.codeprint.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisConcurrencyGuardTest {

    @Test
    @DisplayName("한도(40) 이내면 tryAcquire가 계속 성공한다")
    void 한도_이내_성공() {
        AnalysisConcurrencyGuard guard = new AnalysisConcurrencyGuard();

        for (int i = 0; i < 40; i++) {
            assertThat(guard.tryAcquire()).isTrue();
        }
    }

    @Test
    @DisplayName("한도(40)를 넘으면 tryAcquire가 실패한다")
    void 한도_초과_실패() {
        AnalysisConcurrencyGuard guard = new AnalysisConcurrencyGuard();
        for (int i = 0; i < 40; i++) guard.tryAcquire();

        assertThat(guard.tryAcquire()).isFalse();
    }

    @Test
    @DisplayName("release 후에는 다시 tryAcquire가 성공한다")
    void release_후_재획득_가능() {
        AnalysisConcurrencyGuard guard = new AnalysisConcurrencyGuard();
        for (int i = 0; i < 40; i++) guard.tryAcquire();
        assertThat(guard.tryAcquire()).isFalse();

        guard.release();

        assertThat(guard.tryAcquire()).isTrue();
    }

    @Test
    @DisplayName("동시에 100개 스레드가 몰려도 정확히 40개만 획득에 성공한다(TOCTOU 회귀 방지)")
    void 동시_요청_정확히_한도만큼만_성공() throws InterruptedException {
        AnalysisConcurrencyGuard guard = new AnalysisConcurrencyGuard();
        int threadCount = 100;
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        AtomicInteger succeeded = new AtomicInteger(0);
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    if (guard.tryAcquire()) succeeded.incrementAndGet();
                } catch (InterruptedException ignored) {
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        done.await();
        pool.shutdown();

        assertThat(succeeded.get()).isEqualTo(40);
    }
}
