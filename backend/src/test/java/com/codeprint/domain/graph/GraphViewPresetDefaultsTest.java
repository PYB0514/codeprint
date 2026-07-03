// GraphViewPresetDefaults 단위 테스트 — 슬롯별 기본값 회귀 방지
package com.codeprint.domain.graph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GraphViewPresetDefaultsTest {

    @Test
    @DisplayName("슬롯 1~4 기본 이름과 layoutPreset/labelMode 조합이 고정 순서와 일치")
    void defaultsMatchExpectedOrder() {
        assertThat(GraphViewPresetDefaults.defaultName(1)).isEqualTo("계층-이름");
        assertThat(GraphViewPresetDefaults.defaultName(2)).isEqualTo("계층-주석");
        assertThat(GraphViewPresetDefaults.defaultName(3)).isEqualTo("도메인-이름");
        assertThat(GraphViewPresetDefaults.defaultName(4)).isEqualTo("도메인-주석");

        assertConfig(GraphViewPresetDefaults.defaultConfig(1), "layer", "name");
        assertConfig(GraphViewPresetDefaults.defaultConfig(2), "layer", "comment");
        assertConfig(GraphViewPresetDefaults.defaultConfig(3), "domain", "name");
        assertConfig(GraphViewPresetDefaults.defaultConfig(4), "domain", "comment");
    }

    @SuppressWarnings("unchecked")
    private void assertConfig(Map<String, Object> config, String layoutPreset, String labelMode) {
        assertThat(config.get("layoutPreset")).isEqualTo(layoutPreset);
        assertThat(config.get("labelMode")).isEqualTo(labelMode);
        Map<String, Object> edges = (Map<String, Object>) config.get("edges");
        assertThat(edges.get("broken")).isEqualTo(true);
        assertThat(edges.get("api")).isEqualTo(true);
        assertThat(edges.get("import")).isEqualTo(false);
    }
}
