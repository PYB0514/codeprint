// analysis WarningDetectionPort의 graph 컨텍스트 어댑터 — GraphQueryService 경고 감지에 위임
package com.codeprint.infrastructure.adapter;

import com.codeprint.application.graph.GraphQueryService;
import com.codeprint.domain.analysis.port.WarningDetectionPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class WarningDetectionAdapter implements WarningDetectionPort {

    private final GraphQueryService graphQueryService;

    // graphId의 경고 감지 결과를 그대로 위임 반환
    @Override
    public List<Map<String, Object>> detectWarnings(UUID graphId) {
        return graphQueryService.getWarnings(graphId);
    }
}
