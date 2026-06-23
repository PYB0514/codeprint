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
        List<String> instantiatedClasses,        // 파일 내에서 new X() 로 생성되는 클래스명 목록
        List<DbTableInfo> dbTables,              // @Entity / Prisma model 에서 추출한 테이블 목록
        String repositoryEntityClass,            // extends JpaRepository<EntityName> 에서 추출한 엔티티 클래스명 (null이면 Repository 아님)
        List<ColumnInfo> entityColumns,          // @Entity 클래스의 칼럼 목록 (null이면 Entity 아님)
        List<String> apiCalls,                   // 프론트 axios 호출 경로 목록 ("GET:/api/projects" 형식)
        List<String> controllerMappings,         // 백엔드 @*Mapping 경로 목록 ("/api/projects/{projectId}" 형식)
        List<String> implementedInterfaces,       // "class Foo implements Bar" 에서 추출한 인터페이스명 목록
        List<String> asyncMethods,               // @Async 어노테이션이 붙은 메서드명 목록
        List<String> jsxComponents,              // .tsx/.jsx 에서 사용된 JSX 컴포넌트명 목록 (<ComponentName 패턴)
        List<RawSqlAccess> rawSqlAccesses,       // raw SQL 문자열에서 추출한 테이블 접근 목록
        List<String> frameworkAnnotatedMethods,  // 프레임워크 어노테이션/데코레이터가 붙은 메서드명 목록 (런타임이 호출 → DEAD_CODE 제외용)
        List<String> valueReferencedFunctions,   // 호출이 아닌 값(콜백·고차함수 인자)으로 참조되는 함수명 목록 (DEAD_CODE 제외용)
        Map<String, Integer> functionDefCounts,  // 함수명 → 파일 내 정의 횟수 (≥2면 동명 머지 노드 — HIGH_FAN_OUT 정밀 가드용)
        List<String> declaredTypes,              // 파일이 선언한 클래스/인터페이스명 목록 (파일명≠클래스명 언어의 Type::method 해소용 — Java/C#은 빈 목록)
        List<String> testMethods                 // 테스트 함수명 목록 (Rust #[test]/#[cfg(test)] mod 등 — HIGH_FAN_OUT 제외용. 파일명으로 못 거르는 인라인 테스트 대응)
) {
    // 기존 호출부 호환용 — declaredTypes·testMethods 미지정 시 빈 목록 (파일명 매칭만 쓰는 Java/C# 등)
    public ParsedFile(
            String filePath, String language, List<String> functions, List<String> imports,
            String fileComment, Map<String, String> functionComments, Map<String, List<String>> functionCalls,
            List<String> instantiatedClasses, List<DbTableInfo> dbTables, String repositoryEntityClass,
            List<ColumnInfo> entityColumns, List<String> apiCalls, List<String> controllerMappings,
            List<String> implementedInterfaces, List<String> asyncMethods, List<String> jsxComponents,
            List<RawSqlAccess> rawSqlAccesses, List<String> frameworkAnnotatedMethods,
            List<String> valueReferencedFunctions, Map<String, Integer> functionDefCounts) {
        this(filePath, language, functions, imports, fileComment, functionComments, functionCalls,
                instantiatedClasses, dbTables, repositoryEntityClass, entityColumns, apiCalls, controllerMappings,
                implementedInterfaces, asyncMethods, jsxComponents, rawSqlAccesses, frameworkAnnotatedMethods,
                valueReferencedFunctions, functionDefCounts, List.of(), List.of());
    }

    // declaredTypes 지정·testMethods 미지정 호환용 (Go/TS/Python 등 declaredTypes만 쓰는 경우)
    public ParsedFile(
            String filePath, String language, List<String> functions, List<String> imports,
            String fileComment, Map<String, String> functionComments, Map<String, List<String>> functionCalls,
            List<String> instantiatedClasses, List<DbTableInfo> dbTables, String repositoryEntityClass,
            List<ColumnInfo> entityColumns, List<String> apiCalls, List<String> controllerMappings,
            List<String> implementedInterfaces, List<String> asyncMethods, List<String> jsxComponents,
            List<RawSqlAccess> rawSqlAccesses, List<String> frameworkAnnotatedMethods,
            List<String> valueReferencedFunctions, Map<String, Integer> functionDefCounts,
            List<String> declaredTypes) {
        this(filePath, language, functions, imports, fileComment, functionComments, functionCalls,
                instantiatedClasses, dbTables, repositoryEntityClass, entityColumns, apiCalls, controllerMappings,
                implementedInterfaces, asyncMethods, jsxComponents, rawSqlAccesses, frameworkAnnotatedMethods,
                valueReferencedFunctions, functionDefCounts, declaredTypes, List.of());
    }
}
