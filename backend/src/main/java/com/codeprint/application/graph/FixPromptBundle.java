// 리컨실러 T1 조립 결과 — LLM에 주입할 사실(진단·규칙·코드·제약) 묶음 (§17.10 PromptBundle 계약의 T1 축소판)
package com.codeprint.application.graph;

public record FixPromptBundle(
        String ruleType,
        String diagnosisMessage,
        String targetFunctionName,
        String targetFilePath,
        String language,
        String fixRecipe,
        String fileContent
) {
}
