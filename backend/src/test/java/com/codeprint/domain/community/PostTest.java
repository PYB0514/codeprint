// Post 엔티티 단위 테스트 — null 리스트 기본값·조회수 증가·수정 회귀 방지
package com.codeprint.domain.community;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PostTest {

    private final UUID userId = UUID.randomUUID();
    private final UUID graphId = UUID.randomUUID();

    @Test
    @DisplayName("create — hidden 리스트가 null이면 빈 리스트로 기본값 설정")
    void create_nullListsBecomeEmpty() {
        Post post = Post.create(userId, graphId, "제목", "내용", "praise",
                null, null, null, null);

        assertThat(post.getHiddenLayers()).isEmpty();
        assertThat(post.getHiddenGroups()).isEmpty();
        assertThat(post.getHiddenNodeNames()).isEmpty();
        assertThat(post.getViewCount()).isZero();
    }

    @Test
    @DisplayName("create — 전달된 hidden 리스트는 그대로 보유")
    void create_keepsProvidedLists() {
        Post post = Post.create(userId, graphId, "제목", "내용", "praise",
                List.of("domain"), List.of("g1"), List.of("n1"), "https://repo");

        assertThat(post.getHiddenLayers()).containsExactly("domain");
        assertThat(post.getHiddenGroups()).containsExactly("g1");
        assertThat(post.getHiddenNodeNames()).containsExactly("n1");
        assertThat(post.getRepoUrl()).isEqualTo("https://repo");
    }

    @Test
    @DisplayName("incrementViewCount — 호출마다 조회수 1 증가")
    void incrementViewCount_increments() {
        Post post = Post.create(userId, graphId, "제목", "내용", null,
                null, null, null, null);

        post.incrementViewCount();
        post.incrementViewCount();

        assertThat(post.getViewCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("update — 제목과 내용 수정")
    void update_changesTitleAndContent() {
        Post post = Post.create(userId, graphId, "원제목", "원내용", null,
                null, null, null, null);

        post.update("새제목", "새내용");

        assertThat(post.getTitle()).isEqualTo("새제목");
        assertThat(post.getContent()).isEqualTo("새내용");
    }

    @Test
    @DisplayName("create — 기본 공개범위는 PUBLIC")
    void create_defaultsToPublic() {
        Post post = Post.create(userId, graphId, "제목", "내용", null,
                null, null, null, null);

        assertThat(post.isPublic()).isTrue();
    }

    @Test
    @DisplayName("makePrivate — 비공개로 전환")
    void makePrivate_switchesToPrivate() {
        Post post = Post.create(userId, graphId, "제목", "내용", null,
                null, null, null, null);

        post.makePrivate();

        assertThat(post.isPublic()).isFalse();
    }
}
