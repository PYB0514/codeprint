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
        List<String> frameworkAnnotatedMethods   // 프레임워크 어노테이션/데코레이터가 붙은 메서드명 목록 (런타임이 호출 → DEAD_CODE 제외용)
) {}
