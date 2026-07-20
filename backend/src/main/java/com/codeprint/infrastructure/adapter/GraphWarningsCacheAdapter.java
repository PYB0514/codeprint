// project 컨텍스트 GraphWarningsCachePort의 graph 컨텍스트 어댑터 — 경고 캐시 무효화 위임
package com.codeprint.infrastructure.adapter;

import com.codeprint.application.graph.GraphQueryService;
import com.codeprint.domain.project.port.GraphWarningsCachePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GraphWarningsCacheAdapter implements GraphWarningsCachePort {

    private final GraphQueryService graphQueryService;

    @Override
    public void evictAll() {
        graphQueryService.evictWarningsCache();
    }
}
