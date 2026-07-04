// UserController 프로필 글목록 배치 조립 순수 함수 단위 테스트 — 프로필 N+1 제거 로직 회귀 방지
package com.codeprint.interfaces.api;

import com.codeprint.domain.community.Post;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserControllerAssembleTest {

    private Post newPost(UUID userId) {
        return Post.create(userId, null, "제목", "내용", "GENERAL", null, null, null, null);
    }

    @Test
    @DisplayName("assembleSummaries — 북마크 수와 내 북마크 여부를 정확히 매핑")
    void assembleSummaries_mapsBookmarkMeta() {
        Post post = newPost(UUID.randomUUID());
        UUID pid = post.getId();

        List<UserController.PostSummaryResponse> result = UserController.assembleSummaries(
                List.of(post), Map.of(pid, 5L), Set.of(pid), Set.of());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).bookmarkCount()).isEqualTo(5L);
        assertThat(result.get(0).bookmarkedByMe()).isTrue();
    }

    @Test
    @DisplayName("assembleSummaries — 북마크 없는 글은 0·false 기본값 (GROUP BY 0건 케이스)")
    void assembleSummaries_defaultsForMissing() {
        Post post = newPost(UUID.randomUUID());

        List<UserController.PostSummaryResponse> result = UserController.assembleSummaries(
                List.of(post), Map.of(), Set.of(), Set.of());

        assertThat(result.get(0).bookmarkCount()).isZero();
        assertThat(result.get(0).bookmarkedByMe()).isFalse();
    }

    @Test
    @DisplayName("assembleSummaries — hasGraph: 레거시 graphId 또는 스냅샷 존재 시 true")
    void assembleSummaries_hasGraph() {
        UUID userId = UUID.randomUUID();
        Post legacyGraphPost = Post.create(userId, UUID.randomUUID(), "제목", "내용", "GENERAL", null, null, null, null);
        Post snapshotPost = newPost(userId);
        Post noGraphPost = newPost(userId);

        List<UserController.PostSummaryResponse> result = UserController.assembleSummaries(
                List.of(legacyGraphPost, snapshotPost, noGraphPost), Map.of(), Set.of(), Set.of(snapshotPost.getId()));

        assertThat(result.stream().filter(r -> r.id().equals(legacyGraphPost.getId())).findFirst().orElseThrow().hasGraph()).isTrue();
        assertThat(result.stream().filter(r -> r.id().equals(snapshotPost.getId())).findFirst().orElseThrow().hasGraph()).isTrue();
        assertThat(result.stream().filter(r -> r.id().equals(noGraphPost.getId())).findFirst().orElseThrow().hasGraph()).isFalse();
    }
}
