// 노드 커스터마이징 스타일 Value Object
package com.codeprint.domain.graph;

public record NodeStyle(String color, Integer fontSize, String icon, String groupId) {

    public static NodeStyle defaultStyle() {
        return new NodeStyle(null, null, null, null);
    }
}
