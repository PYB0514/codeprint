// 엣지 커스터마이징 스타일 Value Object
package com.codeprint.domain.graph;

public record EdgeStyle(String color, String lineStyle, Integer thickness) {

    // 기본 스타일 생성
    public static EdgeStyle defaultStyle() {
        return new EdgeStyle(null, "SOLID", null);
    }
}
