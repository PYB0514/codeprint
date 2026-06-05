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
        List<ColumnInfo> entityColumns           // @Entity 클래스의 칼럼 목록 (null이면 Entity 아님)
) {}
