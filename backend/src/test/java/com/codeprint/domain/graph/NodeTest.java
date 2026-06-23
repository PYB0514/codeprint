// Node 엔티티 단위 테스트 — 숨김 토글·좌표 갱신·주석 blank→null 분기 회귀 방지
package com.codeprint.domain.graph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NodeTest {

    // 생성 헬퍼
    private Node newNode() {
        return Node.create(UUID.randomUUID(), NodeType.FUNCTION, "doWork", "src/A.java", "java");
    }

    @Test
    @DisplayName("create — 기본값으로 좌표 0·미숨김 설정")
    void create_setsDefaults() {
        Node n = newNode();
        assertThat(n.getPosX()).isEqualTo(0);
        assertThat(n.getPosY()).isEqualTo(0);
        assertThat(n.isHidden()).isFalse();
        assertThat(n.getType()).isEqualTo(NodeType.FUNCTION);
    }

    @Test
    @DisplayName("updatePosition — 좌표 갱신")
    void updatePosition_updatesCoords() {
        Node n = newNode();

        n.updatePosition(12.5, -7.0);

        assertThat(n.getPosX()).isEqualTo(12.5);
        assertThat(n.getPosY()).isEqualTo(-7.0);
    }

    @Test
    @DisplayName("toggleHidden — 호출마다 숨김 상태 반전")
    void toggleHidden_flipsState() {
        Node n = newNode();

        n.toggleHidden();
        assertThat(n.isHidden()).isTrue();

        n.toggleHidden();
        assertThat(n.isHidden()).isFalse();
    }

    @Test
    @DisplayName("updateAnnotation — 정상 값은 그대로 저장")
    void updateAnnotation_storesValues() {
        Node n = newNode();

        n.updateAnnotation("핵심 함수", "리팩토링 대상");

        assertThat(n.getUserLabel()).isEqualTo("핵심 함수");
        assertThat(n.getUserNote()).isEqualTo("리팩토링 대상");
    }

    @Test
    @DisplayName("updateAnnotation — 공백 문자열은 null로 정규화")
    void updateAnnotation_blankBecomesNull() {
        Node n = newNode();
        n.updateAnnotation("라벨", "메모");

        n.updateAnnotation("  ", "   ");

        assertThat(n.getUserLabel()).isNull();
        assertThat(n.getUserNote()).isNull();
    }

    @Test
    @DisplayName("isNew — 신규 생성 시 true (Spring Data가 persist 선택 → merge SELECT 회피)")
    void isNew_trueOnCreate() {
        assertThat(newNode().isNew()).isTrue();
    }

    @Test
    @DisplayName("isNew — 영속화/로드 후 false (이후 save는 merge=update)")
    void isNew_falseAfterPersistOrLoad() {
        Node n = newNode();

        n.markNotNew();

        assertThat(n.isNew()).isFalse();
    }
}
