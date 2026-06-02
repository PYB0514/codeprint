// 분석 ID Value Object
package com.codeprint.domain.analysis;

import java.util.UUID;

public record AnalysisId(UUID value) {

    public static AnalysisId of(UUID value) {
        return new AnalysisId(value);
    }

    public static AnalysisId newId() {
        return new AnalysisId(UUID.randomUUID());
    }
}
