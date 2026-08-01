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
            Map.entry("cc", "C++"),
            Map.entry("cxx", "C++"),
            Map.entry("hpp", "C++"),
            Map.entry("hh", "C++"),
            Map.entry("c", "C"),
            Map.entry("rb", "Ruby"),
            Map.entry("php", "PHP"),
            Map.entry("swift", "Swift"),
            Map.entry("prisma", "Prisma")
    );

    private static final java.util.Set<String> SUPPORTED = java.util.Set.of(
            "Java", "Kotlin", "TypeScript", "JavaScript", "Python", "Go", "Rust",
            "C#", "C", "C++", "Ruby", "PHP", "Swift", "Prisma", "DockerCompose", "SpringYaml"
    );

    // docker-compose.yml은 .yml/.yaml 확장자를 공유하는 다른 파일(k8s 매니페스트, application.yml 등)과
    // 구분이 안 돼 확장자 매핑에 못 넣는다 — 파일명 자체가 유일한 식별자라 정확히 매칭한다
    // (SERVICE_CALL_CHAIN 환경변수 기반 호출 해소용, decisions/DECISIONS_ANALYSIS.md 참조).
    private static final java.util.Set<String> DOCKER_COMPOSE_FILE_NAMES = java.util.Set.of(
            "docker-compose.yml", "docker-compose.yaml", "compose.yml", "compose.yaml"
    );

    // Spring 설정 파일(application.yml/.yaml/.properties) — @Value+application.yml 조인을 위해
    // 파일명으로 정확히 식별(SERVICE_CALL_CHAIN "변수 조합 URL" ③, decisions/DECISIONS_ANALYSIS.md 참조).
    // 1차 스코프는 기본 프로필만 — application-{profile}.yml 등은 후속.
    private static final java.util.Set<String> SPRING_YAML_FILE_NAMES = java.util.Set.of(
            "application.yml", "application.yaml", "application.properties"
    );

    // 파일명(확장자 무관 특수 케이스 우선) 또는 확장자로 언어를 감지하여 반환
    public static Optional<String> detect(String fileName) {
        if (DOCKER_COMPOSE_FILE_NAMES.contains(fileName)) return Optional.of("DockerCompose");
        if (SPRING_YAML_FILE_NAMES.contains(fileName)) return Optional.of("SpringYaml");
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
