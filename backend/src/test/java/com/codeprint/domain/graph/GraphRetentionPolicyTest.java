// 그래프 보존 정책 단위 테스트 — 비고정 최근 10개 유지·초과분 삭제·고정 보호 경계 회귀 방지
package com.codeprint.domain.graph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GraphRetentionPolicyTest {

    private static final UUID PROJECT = UUID.randomUUID();

    // 지정 생성시각과 고정 슬롯으로 Graph 생성 (createdAt은 정렬 결정성을 위해 reflection으로 주입)
    private static Graph graph(long epochSecond, Integer pinnedSlot) {
        Graph g = Graph.create(PROJECT, UUID.randomUUID());
        try {
            Field createdAt = Graph.class.getDeclaredField("createdAt");
            createdAt.setAccessible(true);
            createdAt.set(g, Instant.ofEpochSecond(epochSecond));
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        if (pinnedSlot != null) g.pin(pinnedSlot);
        return g;
    }

    @Test
    @DisplayName("비고정 10개 이하면 삭제 대상 없음")
    void noEvictionWhenWithinLimit() {
        List<Graph> graphs = new ArrayList<>();
        for (int i = 0; i < 10; i++) graphs.add(graph(i, null));

        assertThat(GraphRetentionPolicy.selectEvictable(graphs)).isEmpty();
    }

    @Test
    @DisplayName("비고정 11개면 가장 오래된 1개가 삭제 대상")
    void evictsOldestBeyondLimit() {
        List<Graph> graphs = new ArrayList<>();
        for (int i = 0; i < 11; i++) graphs.add(graph(i, null)); // i=0이 가장 오래됨

        List<Graph> evict = GraphRetentionPolicy.selectEvictable(graphs);

        assertThat(evict).hasSize(1);
        assertThat(evict.get(0).getCreatedAt()).isEqualTo(Instant.ofEpochSecond(0));
    }

    @Test
    @DisplayName("고정된 버전은 오래되어도 삭제 대상에서 제외 — 비고정 카운트에도 포함되지 않음")
    void pinnedAreAlwaysKeptAndNotCounted() {
        List<Graph> graphs = new ArrayList<>();
        // 고정 2개 (아주 오래됨)
        graphs.add(graph(0, 1));
        graphs.add(graph(1, 2));
        // 비고정 11개 → 그중 가장 오래된 1개만 삭제 대상
        for (int i = 10; i < 21; i++) graphs.add(graph(i, null));

        List<Graph> evict = GraphRetentionPolicy.selectEvictable(graphs);

        assertThat(evict).hasSize(1);
        assertThat(evict.get(0).getCreatedAt()).isEqualTo(Instant.ofEpochSecond(10));
        assertThat(evict).noneMatch(Graph::isPinned);
    }

    @Test
    @DisplayName("빈 목록이면 삭제 대상 없음")
    void emptyListNoEviction() {
        assertThat(GraphRetentionPolicy.selectEvictable(List.of())).isEmpty();
    }
}
