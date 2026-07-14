// 지표 대시보드 조회 서비스 — 집계 read-model(GateMetricsQuery)을 감싸는 얇은 응용 계층
package com.codeprint.application.admin;

import com.codeprint.infrastructure.admin.GateMetricsQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GateMetricsService {

    private final GateMetricsQuery gateMetricsQuery;

    // 지표 대시보드 4층 체계(북극성·경험·실적·가드레일) 현재 값 조회
    @Transactional(readOnly = true)
    public GateMetrics current() {
        return gateMetricsQuery.collect();
    }
}
