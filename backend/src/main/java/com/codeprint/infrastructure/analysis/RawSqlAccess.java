// raw SQL 문자열에서 추출한 테이블 접근 정보 (테이블명 + 읽기/쓰기 구분)
package com.codeprint.infrastructure.analysis;

public record RawSqlAccess(String tableName, boolean isWrite) {}
