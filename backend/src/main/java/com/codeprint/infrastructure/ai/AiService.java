// AI 제공자별 설명 생성 서비스 인터페이스
package com.codeprint.infrastructure.ai;

import com.codeprint.domain.ai.AiProvider;

public interface AiService {

    AiProvider provider();

    // 프롬프트를 받아 AI 응답 텍스트 반환
    String explain(String apiKey, String prompt);
}
