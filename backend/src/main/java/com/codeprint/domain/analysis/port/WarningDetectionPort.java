// PR 리뷰가 graph 컨텍스트 직접 의존 없이 그래프 경고를 얻기 위한 포트 (analysis 도메인 소유)
package com.codeprint.domain.analysis.port;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface WarningDetectionPort {

    // 그래프 ID로 구조 경고 목록 조회
    List<Map<String, Object>> detectWarnings(UUID graphId);
}
