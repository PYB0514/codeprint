// 분석 ID Value Object
package com.codeprint.domain.analysis;

import java.util.UUID;

public record AnalysisId(UUID value) {

    // UUID로 AnalysisId 생성
    public static AnalysisId of(UUID value) {
        return new AnalysisId(value);
    }

    // 새 랜덤 AnalysisId 생성
    public static AnalysisId newId() {
        return new AnalysisId(UUID.randomUUID());
    }
}
