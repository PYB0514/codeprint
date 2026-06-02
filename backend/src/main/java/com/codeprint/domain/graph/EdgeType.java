// 엣지 타입 열거형 (IMPORT / FUNCTION_CALL / DB_READ 등)
package com.codeprint.domain.graph;

public enum EdgeType {
    IMPORT, FUNCTION_CALL, DB_READ, DB_WRITE, API_CALL
}
