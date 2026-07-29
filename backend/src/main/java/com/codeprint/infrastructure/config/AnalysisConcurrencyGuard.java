// 공유 비동기 실행기 부하 감시 — 분산 IP발 동시 분석 폭주 방어(DDoS 갭① 근본 해결)
package com.codeprint.infrastructure.config;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AnalysisConcurrencyGuard {

    // taskExecutor 전체 용량(최대 8스레드+큐 50=58, AsyncConfig 참조)에 여유를 남겨 PR 리뷰 등 다른
    // @Async 작업과 공유하면서도, 큐가 완전히 가득 차 CallerRunsPolicy가 Tomcat 요청 스레드까지
    // 잠식하기 전에 신규 분석 요청을 선제 거부한다. 분산된 여러 IP가 동시에 요청해도(개별 IP는
    // 3분당 1회로 이미 제한돼 있음) 서버 전체 동시 처리량은 이 한도를 넘지 않는다.
    private static final int MAX_IN_FLIGHT = 40;

    private final ThreadPoolTaskExecutor taskExecutor;

    // 현재 활성+대기 작업 수가 한도 이상이면 포화 상태로 판정
    public boolean isAtCapacity() {
        int inFlight = taskExecutor.getActiveCount() + taskExecutor.getThreadPoolExecutor().getQueue().size();
        return inFlight >= MAX_IN_FLIGHT;
    }
}
