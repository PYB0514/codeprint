// ORM 데이터 접근 호출에서 추출한 엔티티 접근 정보 (엔티티 클래스명 + 읽기/쓰기 구분)
// raw SQL(RawSqlAccess, 테이블명 기준)과 달리 ORM은 엔티티 클래스명을 들고, GraphBuilder가 테이블 노드로 해소한다.
package com.codeprint.infrastructure.analysis;

public record DbAccess(String entityClass, boolean isWrite) {}
