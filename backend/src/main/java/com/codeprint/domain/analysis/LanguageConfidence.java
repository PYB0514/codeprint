// 언어별 분석 신뢰도 Value Object
package com.codeprint.domain.analysis;

public record LanguageConfidence(String language, double confidence) {

    public static LanguageConfidence of(String language, double confidence) {
        if (confidence < 0 || confidence > 1) {
            throw new IllegalArgumentException("Confidence must be between 0 and 1");
        }
        return new LanguageConfidence(language, confidence);
    }
}
