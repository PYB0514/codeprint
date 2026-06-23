// Graph 엔티티 단위 테스트 — pin 슬롯 경계 검증·고정 상태 전이·파일 수 기록 회귀 방지
package com.codeprint.domain.graph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GraphTest {

    // 생성 헬퍼
    private Graph newGraph() {
        return Graph.create(UUID.randomUUID(), UUID.randomUUID());
    }

    @Test
    @DisplayName("create — 신규 그래프는 고정되지 않은 상태")
    void create_isNotPinned() {
        Graph g = newGraph();
        assertThat(g.isPinned()).isFalse();
        assertThat(g.getPinnedSlot()).isNull();
    }

    @Test
    @DisplayName("pin — 경계값 슬롯 1과 5는 허용")
    void pin_acceptsBoundarySlots() {
        Graph g1 = newGraph();
        g1.pin(1);
        assertThat(g1.getPinnedSlot()).isEqualTo((short) 1);

        Graph g5 = newGraph();
        g5.pin(5);
        assertThat(g5.getPinnedSlot()).isEqualTo((short) 5);
        assertThat(g5.isPinned()).isTrue();
    }

    @Test
    @DisplayName("pin — 슬롯 0은 거부 (1 미만)")
    void pin_rejectsSlotZero() {
        Graph g = newGraph();
        assertThatThrownBy(() -> g.pin(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("pin — 슬롯 6은 거부 (5 초과)")
    void pin_rejectsSlotSix() {
        Graph g = newGraph();
        assertThatThrownBy(() -> g.pin(6)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("unpin — 고정 상태에서 해제로 전이")
    void unpin_clearsSlot() {
        Graph g = newGraph();
        g.pin(3);

        g.unpin();

        assertThat(g.isPinned()).isFalse();
        assertThat(g.getPinnedSlot()).isNull();
    }

    @Test
    @DisplayName("recordFileCounts — 분석/전체 파일 수 기록")
    void recordFileCounts_storesCounts() {
        Graph g = newGraph();

        g.recordFileCounts(120, 200);

        assertThat(g.getAnalyzedFileCount()).isEqualTo(120);
        assertThat(g.getTotalFileCount()).isEqualTo(200);
    }
}
