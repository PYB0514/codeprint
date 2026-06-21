// NodeCommentService 단위 테스트 — 코멘트 삭제 소유권 검증(작성자만) 회귀 방지
package com.codeprint.application.graph;

import com.codeprint.domain.graph.NodeComment;
import com.codeprint.domain.graph.NodeCommentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NodeCommentServiceTest {

    @Mock
    private NodeCommentRepository repository;

    @InjectMocks
    private NodeCommentService service;

    private final UUID graphId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final UUID otherId = UUID.randomUUID();

    @Test
    @DisplayName("코멘트 작성 — 입력값으로 생성해 저장")
    void addComment_savesCreatedComment() {
        when(repository.save(any(NodeComment.class))).thenAnswer(inv -> inv.getArgument(0));

        NodeComment saved = service.addComment(graphId, "node-1", ownerId, "리뷰 코멘트");

        assertThat(saved.getGraphId()).isEqualTo(graphId);
        assertThat(saved.getNodeId()).isEqualTo("node-1");
        assertThat(saved.getUserId()).isEqualTo(ownerId);
        assertThat(saved.getContent()).isEqualTo("리뷰 코멘트");
        verify(repository).save(any(NodeComment.class));
    }

    @Test
    @DisplayName("코멘트 목록 조회 — 레포지토리 위임")
    void getComments_delegatesToRepository() {
        NodeComment c = NodeComment.create(graphId, "node-1", ownerId, "c");
        when(repository.findByGraphIdAndNodeId(graphId, "node-1")).thenReturn(List.of(c));

        assertThat(service.getComments(graphId, "node-1")).containsExactly(c);
    }

    @Test
    @DisplayName("코멘트 삭제 — 작성자 본인이면 삭제")
    void deleteComment_owner_deletes() {
        NodeComment c = NodeComment.create(graphId, "node-1", ownerId, "c");
        when(repository.findById(c.getId())).thenReturn(Optional.of(c));

        service.deleteComment(c.getId(), ownerId);

        verify(repository).deleteById(c.getId());
    }

    @Test
    @DisplayName("코멘트 삭제 — 존재하지 않으면 IllegalArgumentException")
    void deleteComment_notFound_throws() {
        UUID missing = UUID.randomUUID();
        when(repository.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteComment(missing, ownerId))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).deleteById(any());
    }

    @Test
    @DisplayName("코멘트 삭제 — 작성자가 아니면 IllegalStateException, 삭제 안 함")
    void deleteComment_notOwner_throwsAndSkipsDelete() {
        NodeComment c = NodeComment.create(graphId, "node-1", ownerId, "c");
        when(repository.findById(c.getId())).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.deleteComment(c.getId(), otherId))
                .isInstanceOf(IllegalStateException.class);
        verify(repository, never()).deleteById(any());
    }
}
