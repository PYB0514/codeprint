// CommunityController 배치 조립 순수 함수 단위 테스트 — 피드 N+1 제거 로직 회귀 방지
package com.codeprint.interfaces.api;

import com.codeprint.domain.community.Post;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CommunityControllerAssembleTest {

    private Post newPost(UUID userId) {
        return Post.create(userId, null, "제목", "내용", "GENERAL", null, null, null, null);
    }

    @Test
    @DisplayName("toCountMap — [postId, count] 행을 Map으로 변환 (Integer/Long 모두 long으로)")
    void toCountMap_convertsRows() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();

        Map<UUID, Long> map = CommunityController.toCountMap(List.of(
                new Object[]{p1, 5L},
                new Object[]{p2, 2}  // Integer 도 Number로 안전 변환
        ));

        assertThat(map).containsEntry(p1, 5L).containsEntry(p2, 2L);
    }

    @Test
    @DisplayName("toCountMap — 빈 행 목록은 빈 Map")
    void toCountMap_empty() {
        assertThat(CommunityController.toCountMap(List.of())).isEmpty();
    }

    @Test
    @DisplayName("assemble — 카운트/작성자명/내 여부를 정확히 매핑")
    void assemble_mapsMetadata() {
        UUID author = UUID.randomUUID();
        Post post = newPost(author);
        UUID pid = post.getId();

        List<CommunityController.PostResponse> result = CommunityController.assemble(
                List.of(post),
                Map.of(author, "alice"),
                Map.of(pid, 3L),
                Map.of(pid, 7L),
                Map.of(pid, 4L),
                Set.of(pid),
                Set.of());

        assertThat(result).hasSize(1);
        CommunityController.PostResponse r = result.get(0);
        assertThat(r.authorUsername()).isEqualTo("alice");
        assertThat(r.bookmarkCount()).isEqualTo(3L);
        assertThat(r.likeCount()).isEqualTo(7L);
        assertThat(r.commentCount()).isEqualTo(4L);
        assertThat(r.bookmarkedByMe()).isTrue();   // myBookmarks 에 포함
        assertThat(r.likedByMe()).isFalse();        // myLikes 비어있음
    }

    @Test
    @DisplayName("assemble — 카운트/작성자명이 없는 글은 0·unknown 기본값 (GROUP BY가 0건 글을 빼는 케이스)")
    void assemble_defaultsForMissing() {
        Post post = newPost(UUID.randomUUID());

        List<CommunityController.PostResponse> result = CommunityController.assemble(
                List.of(post),
                Map.of(),   // 작성자명 없음
                Map.of(),   // 북마크 카운트 없음
                Map.of(),
                Map.of(),
                Set.of(),
                Set.of());

        CommunityController.PostResponse r = result.get(0);
        assertThat(r.authorUsername()).isEqualTo("unknown");
        assertThat(r.bookmarkCount()).isZero();
        assertThat(r.likeCount()).isZero();
        assertThat(r.commentCount()).isZero();
        assertThat(r.bookmarkedByMe()).isFalse();
        assertThat(r.likedByMe()).isFalse();
    }
}
