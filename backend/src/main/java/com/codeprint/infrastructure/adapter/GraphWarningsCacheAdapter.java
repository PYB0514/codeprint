// project 컨텍스트 GraphWarningsCachePort의 graph 컨텍스트 어댑터 — 경고 캐시 무효화 위임
package com.codeprint.infrastructure.adapter;

import com.codeprint.application.graph.GraphQueryService;
import com.codeprint.domain.project.port.GraphWarningsCachePort;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
public class GraphWarningsCacheAdapter implements GraphWarningsCachePort {

    private final GraphQueryService graphQueryService;

    // GraphQueryService → ProjectAccessAdapter → ProjectCommandService → 이 어댑터로 되돌아오는 순환 참조를
    // 끊기 위해 @Lazy 필요(WebSocketAuthorizationInterceptor와 동일 패턴, GATE_GAPS [G-8] 참조) — Lombok
    // @RequiredArgsConstructor는 필드의 @Lazy를 생성자 파라미터로 전파하지 않아 명시적 생성자로 작성해야 함.
    // evictAll()은 ProjectCommandService가 완전히 생성된 이후(setGatePolicy 호출 시점)에만 실행되므로 지연 초기화로도 안전
    public GraphWarningsCacheAdapter(@Lazy GraphQueryService graphQueryService) {
        this.graphQueryService = graphQueryService;
    }

    @Override
    public void evictAll() {
        graphQueryService.evictWarningsCache();
    }
}
