// Edge 엔티티 단위 테스트 — 숨김 토글·메타데이터·Persistable isNew 계약 회귀 방지
package com.codeprint.domain.graph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EdgeTest {

    // 생성 헬퍼
    private Edge newEdge() {
        return Edge.create(UUID.randomUUID(), "a-calls-b", EdgeType.FUNCTION_CALL,
                UUID.randomUUID(), UUID.randomUUID());
    }

    @Test
    @DisplayName("create — 기본값으로 미숨김·식별자·타입 설정")
    void create_setsDefaults() {
        Edge e = newEdge();
        assertThat(e.getId()).isNotNull();
        assertThat(e.getEdgeIdentifier()).isEqualTo("a-calls-b");
        assertThat(e.getType()).isEqualTo(EdgeType.FUNCTION_CALL);
        assertThat(e.isHidden()).isFalse();
    }

    @Test
    @DisplayName("toggleHidden — 호출마다 숨김 상태 반전")
    void toggleHidden_flipsState() {
        Edge e = newEdge();

        e.toggleHidden();
        assertThat(e.isHidden()).isTrue();

        e.toggleHidden();
        assertThat(e.isHidden()).isFalse();
    }

    @Test
    @DisplayName("updateMetadata — 메타데이터 저장")
    void updateMetadata_stores() {
        Edge e = newEdge();

        e.updateMetadata(Map.of("sameFile", true));

        assertThat(e.getMetadata()).containsEntry("sameFile", true);
    }

    @Test
    @DisplayName("isNew — 신규 생성 시 true (Spring Data가 persist 선택 → merge SELECT 회피)")
    void isNew_trueOnCreate() {
        assertThat(newEdge().isNew()).isTrue();
    }

    @Test
    @DisplayName("isNew — 영속화/로드 후 false (이후 save는 merge=update)")
    void isNew_falseAfterPersistOrLoad() {
        Edge e = newEdge();

        e.markNotNew();

        assertThat(e.isNew()).isFalse();
    }
}
