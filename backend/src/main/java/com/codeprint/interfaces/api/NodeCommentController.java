// 노드 코멘트 REST API — 그래프 노드에 코멘트 추가/조회/삭제
package com.codeprint.interfaces.api;

import com.codeprint.application.graph.GraphFacade;
import com.codeprint.application.graph.NodeCommentService;
import com.codeprint.domain.graph.NodeComment;
import com.codeprint.domain.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class NodeCommentController {

    private final NodeCommentService nodeCommentService;
    private final GraphFacade graphFacade;

    // 노드 코멘트 목록 조회 — 소유자이거나 프로젝트가 공개면 접근 가능(읽기 전용, 공유 뷰어용)
    @GetMapping("/api/graphs/{graphId}/nodes/{nodeId}/comments")
    public ResponseEntity<List<Map<String, Object>>> getComments(
            @PathVariable UUID graphId,
            @PathVariable String nodeId,
            @AuthenticationPrincipal User user) {

        graphFacade.verifyGraphReadAccess(graphId, user != null ? user.getId() : null);

        List<Map<String, Object>> result = nodeCommentService.getComments(graphId, nodeId).stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(result);
    }

    // 노드 코멘트 작성
    @PostMapping("/api/graphs/{graphId}/nodes/{nodeId}/comments")
    public ResponseEntity<Map<String, Object>> addComment(
            @PathVariable UUID graphId,
            @PathVariable String nodeId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal User user) {

        String content = body.get("content");
        if (content == null || content.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        verifyOwnership(graphId, user);

        NodeComment comment = nodeCommentService.addComment(graphId, nodeId, user.getId(), content.trim());
        return ResponseEntity.status(201).body(toResponse(comment));
    }

    // 노드 코멘트 삭제 — 작성자 본인만 가능
    @DeleteMapping("/api/graphs/{graphId}/nodes/{nodeId}/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @PathVariable UUID graphId,
            @PathVariable UUID commentId,
            @PathVariable UUID nodeId,
            @AuthenticationPrincipal User user) {

        verifyOwnership(graphId, user);
        nodeCommentService.deleteComment(commentId, user.getId());
        return ResponseEntity.noContent().build();
    }

    // 그래프 → 프로젝트 소유권 검증
    private void verifyOwnership(UUID graphId, User user) {
        graphFacade.verifyGraphOwnership(graphId, user.getId());
    }

    // NodeComment → 응답 Map 변환
    private Map<String, Object> toResponse(NodeComment c) {
        return Map.of(
                "id", c.getId(),
                "nodeId", c.getNodeId(),
                "userId", c.getUserId(),
                "content", c.getContent(),
                "createdAt", c.getCreatedAt().toEpochMilli()
        );
    }
}
