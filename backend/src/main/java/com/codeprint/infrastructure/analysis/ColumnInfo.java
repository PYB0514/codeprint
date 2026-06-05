// @Entity 클래스에서 추출한 DB 칼럼 정보 DTO
package com.codeprint.infrastructure.analysis;

public record ColumnInfo(
        String fieldName,
        String columnName,
        String javaType
) {}
