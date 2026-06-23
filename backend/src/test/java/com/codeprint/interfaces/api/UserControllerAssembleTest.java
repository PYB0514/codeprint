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
                List.of(post), Map.of(pid, 5L), Set.of(pid));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).bookmarkCount()).isEqualTo(5L);
        assertThat(result.get(0).bookmarkedByMe()).isTrue();
    }

    @Test
    @DisplayName("assembleSummaries — 북마크 없는 글은 0·false 기본값 (GROUP BY 0건 케이스)")
    void assembleSummaries_defaultsForMissing() {
        Post post = newPost(UUID.randomUUID());

        List<UserController.PostSummaryResponse> result = UserController.assembleSummaries(
                List.of(post), Map.of(), Set.of());

        assertThat(result.get(0).bookmarkCount()).isZero();
        assertThat(result.get(0).bookmarkedByMe()).isFalse();
    }
}
