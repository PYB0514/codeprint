// 파일 하나의 정적 분석 결과 DTO
package com.codeprint.infrastructure.analysis;

import java.util.List;

public record ParsedFile(
        String filePath,
        String language,
        List<String> functions,
        List<String> imports
) {}
