// 공유 비동기 실행기(taskExecutor) 슬롯 admission 제어 — 분산 IP발 동시 분석·PR 리뷰 폭주 방어(DDoS 갭① 근본 해결)
// (2026-07-30 적대적 검증에서 TOCTOU 레이스 CONFIRMED — 통계 읽기(isAtCapacity) 방식은 tryAcquire 시점과 실제
// taskExecutor 제출 시점 사이에 GitHub API 왕복이 끼어 있어 레이스 윈도우가 네트워크 RTT 단위로 벌어졌다.
// 세마포어로 슬롯을 원자적으로 예약하는 방식으로 교체 — decisions/DECISIONS_BACKEND.md 참조)
package com.codeprint.infrastructure.config;

import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;

@Component
public class AnalysisConcurrencyGuard {

    // taskExecutor 전체 용량(최대 8스레드+큐 50=58, AsyncConfig 참조)에 여유를 남긴 값. 분석과 PR 리뷰
    // (웹훅·리컨실리에이션) 둘 다 이 세마포어를 통해서만 taskExecutor에 제출되므로, 두 경로를 합쳐서
    // 정확히 이 개수까지만 동시 진행을 허용한다.
    private static final int MAX_IN_FLIGHT = 40;

    private final Semaphore permits = new Semaphore(MAX_IN_FLIGHT);

    // 슬롯을 원자적으로 예약 시도 — 성공(true) 시 작업이 끝나는 시점에 반드시 release()를 호출해야 한다(try/finally)
    public boolean tryAcquire() {
        return permits.tryAcquire();
    }

    // 예약한 슬롯 반납
    public void release() {
        permits.release();
    }
}
