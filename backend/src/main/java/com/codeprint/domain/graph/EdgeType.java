// 엣지 타입 열거형 (IMPORT / FUNCTION_CALL / DB_READ 등)
package com.codeprint.domain.graph;

public enum EdgeType {
    CONTAINS, IMPORT, INSTANTIATION, FUNCTION_CALL,
    DB_READ, DB_WRITE,
    DB_CREATE, DB_UPDATE, DB_DELETE,
    API_CALL, SERVICE_CALL,
    FIELD_DEPENDENCY
}
