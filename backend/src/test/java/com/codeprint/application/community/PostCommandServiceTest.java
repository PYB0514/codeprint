// PostCommandService 단위 테스트 — 게시글/댓글 소유권 검증(IDOR 방지)·not-found 분기 회귀 방지
package com.codeprint.application.community;

import com.codeprint.domain.community.Comment;
import com.codeprint.domain.community.Post;
import com.codeprint.domain.community.PostGraphSnapshot;
import com.codeprint.domain.community.PostRepository;
import com.codeprint.infrastructure.storage.S3Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostCommandServiceTest {

    @Mock private PostRepository postRepository;
    @Mock private S3Service s3Service;

    private PostCommandService service;

    @BeforeEach
    void setUp() {
        service = new PostCommandService(postRepository, s3Service);
    }

    private Post postOwnedBy(UUID ownerId) {
        return Post.create(ownerId, UUID.randomUUID(), "title", "content", "GENERAL", null, null, null, null);
    }

    @Test
    @DisplayName("createPost — 게시글 생성·저장 후 반환")
    void createPost_savesAndReturns() {
        UUID userId = UUID.randomUUID();
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

        Post created = service.createPost(userId, UUID.randomUUID(), "t", "c", "GENERAL", null, null, null, null);

        assertThat(created.getUserId()).isEqualTo(userId);
        verify(postRepository).save(any(Post.class));
    }

    // --- addComment ---

    @Test
    @DisplayName("addComment — 게시글이 있으면 댓글 저장")
    void addComment_postExists_saves() {
        UUID postId = UUID.randomUUID();
        when(postRepository.findById(postId)).thenReturn(Optional.of(postOwnedBy(UUID.randomUUID())));
        when(postRepository.saveComment(any(Comment.class))).thenAnswer(inv -> inv.getArgument(0));

        Comment c = service.addComment(postId, UUID.randomUUID(), "hi");

        assertThat(c).isNotNull();
        verify(postRepository).saveComment(any(Comment.class));
    }

    @Test
    @DisplayName("addComment — 존재하지 않는 게시글이면 IllegalArgumentException")
    void addComment_postNotFound_throws() {
        UUID postId = UUID.randomUUID();
        when(postRepository.findById(postId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addComment(postId, UUID.randomUUID(), "hi"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Post not found");
        verify(postRepository, never()).saveComment(any());
    }

    // --- deleteComment: 소유권 ---

    @Test
    @DisplayName("deleteComment — 작성자 본인이면 삭제")
    void deleteComment_owner_deletes() {
        UUID ownerId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        Comment comment = Comment.create(UUID.randomUUID(), ownerId, "c");
        when(postRepository.findCommentById(commentId)).thenReturn(Optional.of(comment));

        service.deleteComment(commentId, ownerId);

        verify(postRepository).deleteCommentById(commentId);
    }

    @Test
    @DisplayName("deleteComment — 작성자가 아니면 403 FORBIDDEN, 삭제 안 함")
    void deleteComment_notOwner_forbidden() {
        UUID ownerId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        Comment comment = Comment.create(UUID.randomUUID(), ownerId, "c");
        when(postRepository.findCommentById(commentId)).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> service.deleteComment(commentId, UUID.randomUUID()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(postRepository, never()).deleteCommentById(any());
    }

    @Test
    @DisplayName("deleteComment — 존재하지 않는 댓글이면 IllegalArgumentException")
    void deleteComment_notFound_throws() {
        UUID commentId = UUID.randomUUID();
        when(postRepository.findCommentById(commentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteComment(commentId, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Comment not found");
        verify(postRepository, never()).deleteCommentById(any());
    }

    // --- updatePost: 소유권 ---

    @Test
    @DisplayName("updatePost — 작성자 본인이면 제목/내용 수정·저장")
    void updatePost_owner_updatesAndSaves() {
        UUID ownerId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        Post post = postOwnedBy(ownerId);
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(postRepository.save(post)).thenReturn(post);

        Post result = service.updatePost(postId, ownerId, "newTitle", "newContent");

        assertThat(result.getTitle()).isEqualTo("newTitle");
        assertThat(result.getContent()).isEqualTo("newContent");
        verify(postRepository).save(post);
    }

    @Test
    @DisplayName("updatePost — 작성자가 아니면 403 FORBIDDEN, 저장 안 함")
    void updatePost_notOwner_forbidden() {
        UUID ownerId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        Post post = postOwnedBy(ownerId);
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> service.updatePost(postId, UUID.randomUUID(), "t", "c"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(postRepository, never()).save(any());
    }

    @Test
    @DisplayName("updatePost — 존재하지 않는 게시글이면 IllegalArgumentException")
    void updatePost_notFound_throws() {
        UUID postId = UUID.randomUUID();
        when(postRepository.findById(postId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updatePost(postId, UUID.randomUUID(), "t", "c"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Post not found");
        verify(postRepository, never()).save(any());
    }

    // --- deletePost: 소유권 ---

    @Test
    @DisplayName("deletePost — 작성자 본인이면 삭제")
    void deletePost_owner_deletes() {
        UUID ownerId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        when(postRepository.findById(postId)).thenReturn(Optional.of(postOwnedBy(ownerId)));

        service.deletePost(postId, ownerId);

        verify(postRepository).deleteById(postId);
    }

    @Test
    @DisplayName("deletePost — 작성자가 아니면 IllegalStateException, 삭제 안 함")
    void deletePost_notOwner_rejected() {
        UUID ownerId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        when(postRepository.findById(postId)).thenReturn(Optional.of(postOwnedBy(ownerId)));

        assertThatThrownBy(() -> service.deletePost(postId, UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Not authorized");
        verify(postRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("deletePost — 존재하지 않는 게시글이면 IllegalArgumentException")
    void deletePost_notFound_throws() {
        UUID postId = UUID.randomUUID();
        when(postRepository.findById(postId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deletePost(postId, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
        verify(postRepository, never()).deleteById(any());
    }

    // --- makePrivate ---

    @Test
    @DisplayName("makePrivate — 게시글을 비공개로 전환 후 저장")
    void makePrivate_switchesAndSaves() {
        UUID postId = UUID.randomUUID();
        Post post = postOwnedBy(UUID.randomUUID());
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(postRepository.save(post)).thenReturn(post);

        Post result = service.makePrivate(postId);

        assertThat(result.isPublic()).isFalse();
        verify(postRepository).save(post);
    }

    @Test
    @DisplayName("makePrivate — 존재하지 않는 게시글이면 IllegalArgumentException")
    void makePrivate_notFound_throws() {
        UUID postId = UUID.randomUUID();
        when(postRepository.findById(postId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.makePrivate(postId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Post not found");
        verify(postRepository, never()).save(any());
    }

    // --- saveGraphSnapshots ---

    @Test
    @DisplayName("saveGraphSnapshots — 스냅샷 목록을 순서대로 position 부여해 저장")
    void saveGraphSnapshots_assignsPositionInOrder() {
        UUID postId = UUID.randomUUID();
        UUID projectId1 = UUID.randomUUID();
        UUID graphId1 = UUID.randomUUID();
        UUID projectId2 = UUID.randomUUID();
        UUID graphId2 = UUID.randomUUID();

        service.saveGraphSnapshots(postId, List.of(
                new PostCommandService.SnapshotToSave(projectId1, graphId1, Map.of("layoutPreset", "layer")),
                new PostCommandService.SnapshotToSave(projectId2, graphId2, Map.of("layoutPreset", "domain"))));

        org.mockito.ArgumentCaptor<List<PostGraphSnapshot>> captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(postRepository).saveSnapshots(captor.capture());
        List<PostGraphSnapshot> saved = captor.getValue();
        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getPosition()).isZero();
        assertThat(saved.get(0).getProjectId()).isEqualTo(projectId1);
        assertThat(saved.get(1).getPosition()).isEqualTo(1);
        assertThat(saved.get(1).getProjectId()).isEqualTo(projectId2);
    }
}
