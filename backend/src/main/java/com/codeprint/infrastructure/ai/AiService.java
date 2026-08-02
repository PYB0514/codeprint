// AI 제공자별 프롬프트 응답 생성 서비스 인터페이스
package com.codeprint.infrastructure.ai;

import com.codeprint.shared.ai.AiProvider;

public interface AiService {

    AiProvider provider();

    // 프롬프트를 받아 AI 응답 텍스트 반환(실패 시 예외)
    String generate(String apiKey, String prompt);
}
