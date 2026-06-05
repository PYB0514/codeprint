// @Entity 또는 Prisma model에서 추출한 DB 테이블 정보
package com.codeprint.infrastructure.analysis;

public record DbTableInfo(
        String tableName,   // DB 실제 테이블명 (또는 @Table name)
        String className    // 소스 코드 클래스/모델명
) {}
