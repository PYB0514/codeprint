// 인메모리 캐시 설정 — 그래프 노드·엣지 조회 결과를 TTL 기반으로 캐싱
package com.codeprint.infrastructure.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {

    // 그래프 노드·엣지 캐시 — 분석 완료 후 자주 재조회되는 데이터
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager("graphNodes", "graphEdges", "graphWarnings");
        manager.setCaffeine(
            Caffeine.newBuilder()
                .maximumSize(200)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                // maximumSize는 엔트리 개수 기준이라 노드 수천+엣지 수천짜리 그래프 하나가
                // 힙을 다 채워도 200개 미만이면 계속 쌓임 — 메모리 압박 시 GC가 회수하도록 보강
                .softValues()
        );
        return manager;
    }
}
