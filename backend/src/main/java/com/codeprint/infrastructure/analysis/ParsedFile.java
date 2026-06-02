// 파일 하나의 정적 분석 결과 DTO
package com.codeprint.infrastructure.analysis;

import java.util.List;
import java.util.Map;

public record ParsedFile(
        String filePath,
        String language,
        List<String> functions,
        List<String> imports,
        String fileComment,
        Map<String, String> functionComments,    // 함수명 → 주석
        Map<String, List<String>> functionCalls, // 함수명 → 호출하는 함수명 목록
        List<String> instantiatedClasses         // 파일 내에서 new X() 로 생성되는 클래스명 목록
) {}
