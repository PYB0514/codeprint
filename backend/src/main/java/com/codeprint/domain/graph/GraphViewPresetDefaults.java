// 그래프 뷰 프리셋 슬롯(1~4) 기본값 계산 — 순수 함수, 저장 안 된 슬롯을 조회할 때 공통으로 재사용
package com.codeprint.domain.graph;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GraphViewPresetDefaults {

    private static final String[][] SLOT_DEFAULTS = {
        {"계층-이름",   "layer",  "name"},
        {"계층-주석",   "layer",  "comment"},
        {"도메인-이름", "domain", "name"},
        {"도메인-주석", "domain", "comment"},
    };

    private GraphViewPresetDefaults() {}

    // 슬롯(1~4)의 기본 이름
    public static String defaultName(int slot) {
        return SLOT_DEFAULTS[slot - 1][0];
    }

    // 슬롯(1~4)의 기본 config 생성
    public static Map<String, Object> defaultConfig(int slot) {
        String[] def = SLOT_DEFAULTS[slot - 1];
        return buildConfig(def[1], def[2]);
    }

    private static Map<String, Object> buildConfig(String layoutPreset, String labelMode) {
        Map<String, Object> edges = new LinkedHashMap<>();
        edges.put("import", false);
        edges.put("call", false);
        edges.put("inst", false);
        edges.put("broken", true);
        edges.put("db", false);
        edges.put("api", true);
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("layoutPreset", layoutPreset);
        config.put("labelMode", labelMode);
        config.put("edges", edges);
        config.put("opaqueLayerSet", List.of());
        config.put("hiddenLayers", List.of());
        config.put("hiddenGroups", List.of());
        config.put("hiddenNodes", List.of());
        return config;
    }
}
