// @Async 실행기 설정 — 무제한 스레드 생성 대신 유계 풀+큐로 동시 분석 폭증 시 메모리 위험 방지(안정성 갭 D)
package com.codeprint.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    // 분석/PR 리뷰는 클론+파싱을 동반해 CPU·메모리 부담이 커 동시 실행 수를 제한 — Railway 메모리 제약 하에서의 안전선
    private static final int CORE_POOL_SIZE = 4;
    private static final int MAX_POOL_SIZE = 8;
    private static final int QUEUE_CAPACITY = 50;

    // AsyncConfigurer가 이 executor를 명시적으로 지정하므로, WebSocket 관련 다른 TaskExecutor 빈들과의
    // 모호성(둘 이상의 TaskExecutor 후보 발견 시 Spring이 무제한 SimpleAsyncTaskExecutor로 폴백하던 문제)이 사라진다.
    @Bean(name = "taskExecutor")
    public ThreadPoolTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(CORE_POOL_SIZE);
        executor.setMaxPoolSize(MAX_POOL_SIZE);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setThreadNamePrefix("codeprint-async-");
        // 큐까지 가득 차면 작업을 버리는 대신 호출자 스레드에서 직접 실행 — 유실 없는 배압(backpressure)
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    @Override
    public Executor getAsyncExecutor() {
        return taskExecutor();
    }

    // void 반환 @Async 메서드에서 빠져나온 예외(현재 각 메서드가 자체적으로 흡수하지만, 누락 시 조용히 사라지는 것 방지)
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return this::logUncaughtAsyncException;
    }

    private void logUncaughtAsyncException(Throwable ex, Method method, Object... params) {
        log.error("@Async 메서드에서 처리되지 않은 예외: {}", method, ex);
    }
}
