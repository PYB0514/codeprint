// 노드 코멘트 CRUD 애플리케이션 서비스
package com.codeprint.application.graph;

import com.codeprint.domain.graph.NodeComment;
import com.codeprint.domain.graph.NodeCommentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NodeCommentService {

    private final NodeCommentRepository nodeCommentRepository;

    // 코멘트 작성
    @Transactional
    public NodeComment addComment(UUID graphId, UUID nodeId, UUID userId, String content) {
        NodeComment comment = NodeComment.create(graphId, nodeId, userId, content);
        return nodeCommentRepository.save(comment);
    }

    // 특정 노드 코멘트 목록 조회
    @Transactional(readOnly = true)
    public List<NodeComment> getComments(UUID graphId, UUID nodeId) {
        return nodeCommentRepository.findByGraphIdAndNodeId(graphId, nodeId);
    }

    // 코멘트 삭제 — 작성자 본인만 가능
    @Transactional
    public void deleteComment(UUID commentId, UUID userId) {
        NodeComment comment = nodeCommentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("코멘트를 찾을 수 없습니다."));
        if (!comment.isOwner(userId)) {
            throw new IllegalStateException("본인의 코멘트만 삭제할 수 있습니다.");
        }
        nodeCommentRepository.deleteById(commentId);
    }
}
