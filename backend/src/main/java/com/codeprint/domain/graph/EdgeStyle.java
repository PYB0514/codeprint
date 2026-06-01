package com.codeprint.domain.graph;

public record EdgeStyle(String color, String lineStyle, Integer thickness) {

    public static EdgeStyle defaultStyle() {
        return new EdgeStyle(null, "SOLID", null);
    }
}
