// 파일 확장자로 프로그래밍 언어를 감지하는 유틸리티
package com.codeprint.infrastructure.analysis;

import java.util.Map;
import java.util.Optional;

public class LanguageDetector {

    private static final Map<String, String> EXT_TO_LANG = Map.ofEntries(
            Map.entry("java", "Java"),
            Map.entry("kt", "Kotlin"),
            Map.entry("ts", "TypeScript"),
            Map.entry("tsx", "TypeScript"),
            Map.entry("js", "JavaScript"),
            Map.entry("jsx", "JavaScript"),
            Map.entry("py", "Python"),
            Map.entry("go", "Go"),
            Map.entry("rs", "Rust"),
            Map.entry("cs", "C#"),
            Map.entry("cpp", "C++"),
            Map.entry("c", "C"),
            Map.entry("rb", "Ruby"),
            Map.entry("php", "PHP"),
            Map.entry("swift", "Swift"),
            Map.entry("prisma", "Prisma")
    );

    private static final java.util.Set<String> SUPPORTED = java.util.Set.of(
            "Java", "Kotlin", "TypeScript", "JavaScript", "Python", "Go", "Rust",
            "C#", "Ruby", "PHP", "Swift", "Prisma"
    );

    // 파일 확장자로 프로그래밍 언어를 감지하여 반환
    public static Optional<String> detect(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot == -1) return Optional.empty();
        String ext = fileName.substring(dot + 1).toLowerCase();
        return Optional.ofNullable(EXT_TO_LANG.get(ext));
    }

    // 해당 언어가 정적 분석 지원 대상인지 확인
    public static boolean isSupported(String language) {
        return SUPPORTED.contains(language);
    }
}
