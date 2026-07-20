// CommunityController 배치 조립 순수 함수 단위 테스트 — 피드 N+1 제거 로직 회귀 방지
package com.codeprint.interfaces.api;

import com.codeprint.domain.community.Post;
import com.codeprint.domain.community.port.GraphReadPort;
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

    private Post newPostWithRepo(UUID userId, String repoUrl) {
        return Post.create(userId, null, "제목", "내용", "GENERAL", null, null, null, repoUrl);
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
                Set.of(),
                Set.of(pid));

        assertThat(result).hasSize(1);
        CommunityController.PostResponse r = result.get(0);
        assertThat(r.authorUsername()).isEqualTo("alice");
        assertThat(r.bookmarkCount()).isEqualTo(3L);
        assertThat(r.likeCount()).isEqualTo(7L);
        assertThat(r.commentCount()).isEqualTo(4L);
        assertThat(r.bookmarkedByMe()).isTrue();   // myBookmarks 에 포함
        assertThat(r.likedByMe()).isFalse();        // myLikes 비어있음
        assertThat(r.hasGraph()).isTrue();          // postsWithSnapshots 에 포함
    }

    @Test
    @DisplayName("assemble — hasGraph: 레거시 graphId만 있어도 true, 스냅샷도 graphId도 없으면 false")
    void assemble_hasGraph_legacyOrSnapshotOrNeither() {
        UUID author = UUID.randomUUID();
        Post legacyGraphPost = Post.create(author, UUID.randomUUID(), "제목", "내용", "GENERAL", null, null, null, null);
        Post noGraphPost = newPost(author);

        List<CommunityController.PostResponse> result = CommunityController.assemble(
                List.of(legacyGraphPost, noGraphPost),
                Map.of(author, "alice"),
                Map.of(), Map.of(), Map.of(),
                Set.of(), Set.of(),
                Set.of());

        CommunityController.PostResponse legacy = result.stream().filter(r -> r.id().equals(legacyGraphPost.getId())).findFirst().orElseThrow();
        CommunityController.PostResponse none = result.stream().filter(r -> r.id().equals(noGraphPost.getId())).findFirst().orElseThrow();
        assertThat(legacy.hasGraph()).isTrue();
        assertThat(none.hasGraph()).isFalse();
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
                Set.of(),
                Set.of());

        CommunityController.PostResponse r = result.get(0);
        assertThat(r.authorUsername()).isEqualTo("unknown");
        assertThat(r.bookmarkCount()).isZero();
        assertThat(r.likeCount()).isZero();
        assertThat(r.commentCount()).isZero();
        assertThat(r.bookmarkedByMe()).isFalse();
        assertThat(r.likedByMe()).isFalse();
        assertThat(r.hasGraph()).isFalse();
    }

    @Test
    @DisplayName("assemble — ownRepo: 레포 owner와 작성자명이 일치할 때만 true")
    void assemble_ownRepo() {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        Post ownPost = newPostWithRepo(alice, "https://github.com/alice/myrepo");
        Post externalPost = newPostWithRepo(bob, "https://github.com/someone-else/theirrepo");

        List<CommunityController.PostResponse> result = CommunityController.assemble(
                List.of(ownPost, externalPost),
                Map.of(alice, "alice", bob, "bob"),
                Map.of(), Map.of(), Map.of(),
                Set.of(), Set.of(),
                Set.of());

        assertThat(result.stream().filter(r -> r.id().equals(ownPost.getId())).findFirst().orElseThrow().ownRepo()).isTrue();
        assertThat(result.stream().filter(r -> r.id().equals(externalPost.getId())).findFirst().orElseThrow().ownRepo()).isFalse();
    }

    @Test
    @DisplayName("toNodeMaps — 숨김 노드 제외, comment는 있을 때만 필드 포함")
    void toNodeMaps_filtersHiddenAndOmitsNullComment() {
        GraphReadPort.NodeView visible = new GraphReadPort.NodeView(
                UUID.randomUUID(), "CLASS", "Foo", "Foo.java", "java", 1.0, 2.0, "주석", false);
        GraphReadPort.NodeView hidden = new GraphReadPort.NodeView(
                UUID.randomUUID(), "CLASS", "Bar", "Bar.java", "java", 3.0, 4.0, null, true);

        List<Map<String, Object>> result = CommunityController.toNodeMaps(List.of(visible, hidden));

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("name", "Foo").containsEntry("comment", "주석");
    }

    @Test
    @DisplayName("toEdgeMaps — 숨김 엣지 제외")
    void toEdgeMaps_filtersHidden() {
        GraphReadPort.EdgeView visible = new GraphReadPort.EdgeView(
                UUID.randomUUID(), "CALL", UUID.randomUUID(), UUID.randomUUID(), "a->b", false);
        GraphReadPort.EdgeView hidden = new GraphReadPort.EdgeView(
                UUID.randomUUID(), "CALL", UUID.randomUUID(), UUID.randomUUID(), "c->d", true);

        List<Map<String, Object>> result = CommunityController.toEdgeMaps(List.of(visible, hidden));

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("edgeIdentifier", "a->b");
    }

    @Test
    @DisplayName("applyPostHiddenFilter — hiddenNodeNames에 있는 노드는 제외(permitAll 응답에서 실제로 빠져야 함)")
    void applyPostHiddenFilter_excludesHiddenNodeNames() {
        GraphReadPort.NodeView secret = new GraphReadPort.NodeView(
                UUID.randomUUID(), "FUNCTION", "collectUserSsn", "domain/user/User.java", "java", 0, 0, null, false);
        GraphReadPort.NodeView normal = new GraphReadPort.NodeView(
                UUID.randomUUID(), "FUNCTION", "getUsername", "domain/user/User.java", "java", 0, 0, null, false);

        List<GraphReadPort.NodeView> result = CommunityController.applyPostHiddenFilter(
                List.of(secret, normal), List.of(), List.of(), List.of("collectUserSsn"));

        assertThat(result).extracting(GraphReadPort.NodeView::name).containsExactly("getUsername");
    }

    @Test
    @DisplayName("applyPostHiddenFilter — hiddenLayers에 있는 레이어의 노드는 filePath로 판별해 제외")
    void applyPostHiddenFilter_excludesHiddenLayers() {
        GraphReadPort.NodeView domainNode = new GraphReadPort.NodeView(
                UUID.randomUUID(), "FILE", "User.java", "src/domain/user/User.java", "java", 0, 0, null, false);
        GraphReadPort.NodeView infraNode = new GraphReadPort.NodeView(
                UUID.randomUUID(), "FILE", "UserJpaRepository.java", "src/infrastructure/persistence/UserJpaRepository.java", "java", 0, 0, null, false);

        List<GraphReadPort.NodeView> result = CommunityController.applyPostHiddenFilter(
                List.of(domainNode, infraNode), List.of("domain"), List.of(), List.of());

        assertThat(result).extracting(GraphReadPort.NodeView::name).containsExactly("UserJpaRepository.java");
    }

    @Test
    @DisplayName("applyPostHiddenFilter — 그래프 자체 is_hidden 노드도 함께 제외")
    void applyPostHiddenFilter_excludesGraphHiddenNodes() {
        GraphReadPort.NodeView visible = new GraphReadPort.NodeView(
                UUID.randomUUID(), "FILE", "Foo.java", "Foo.java", "java", 0, 0, null, false);
        GraphReadPort.NodeView graphHidden = new GraphReadPort.NodeView(
                UUID.randomUUID(), "FILE", "Bar.java", "Bar.java", "java", 0, 0, null, true);

        List<GraphReadPort.NodeView> result = CommunityController.applyPostHiddenFilter(
                List.of(visible, graphHidden), List.of(), List.of(), List.of());

        assertThat(result).extracting(GraphReadPort.NodeView::name).containsExactly("Foo.java");
    }
}
