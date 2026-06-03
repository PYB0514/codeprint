// 커뮤니티 게시글/댓글 REST API 컨트롤러
package com.codeprint.interfaces.api;

import com.codeprint.application.community.PostCommandService;
import com.codeprint.domain.community.Comment;
import com.codeprint.domain.community.Post;
import com.codeprint.domain.user.User;
import com.codeprint.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/community")
@RequiredArgsConstructor
public class CommunityController {

    private final PostCommandService postCommandService;
    private final UserRepository userRepository;

    // 게시글 목록 조회 (페이지)
    @GetMapping("/posts")
    public ResponseEntity<List<PostResponse>> getPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<PostResponse> posts = postCommandService.getPosts(page, size).stream()
                .map(p -> toPostResponse(p, null))
                .toList();
        return ResponseEntity.ok(posts);
    }

    // 게시글 단건 + 댓글 목록 조회
    @GetMapping("/posts/{postId}")
    public ResponseEntity<PostDetailResponse> getPost(@PathVariable UUID postId) {
        Post post = postCommandService.getPosts(0, Integer.MAX_VALUE).stream()
                .filter(p -> p.getId().equals(postId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Post not found: " + postId));
        List<CommentResponse> comments = postCommandService.getComments(postId).stream()
                .map(this::toCommentResponse)
                .toList();
        return ResponseEntity.ok(new PostDetailResponse(toPostResponse(post, null), comments));
    }

    // 새 게시글 작성
    @PostMapping("/posts")
    public ResponseEntity<PostResponse> createPost(
            @RequestBody CreatePostRequest request,
            @AuthenticationPrincipal User user) {
        Post post = postCommandService.createPost(
                user.getId(),
                request.graphId(),
                request.title(),
                request.content(),
                request.feedbackType());
        return ResponseEntity.status(201).body(toPostResponse(post, user.getUsername()));
    }

    // 댓글 작성
    @PostMapping("/posts/{postId}/comments")
    public ResponseEntity<CommentResponse> addComment(
            @PathVariable UUID postId,
            @RequestBody CreateCommentRequest request,
            @AuthenticationPrincipal User user) {
        Comment comment = postCommandService.addComment(postId, user.getId(), request.content());
        return ResponseEntity.status(201).body(toCommentResponse(comment));
    }

    // 게시글 삭제
    @DeleteMapping("/posts/{postId}")
    public ResponseEntity<Void> deletePost(
            @PathVariable UUID postId,
            @AuthenticationPrincipal User user) {
        postCommandService.deletePost(postId, user.getId());
        return ResponseEntity.noContent().build();
    }

    // Post 엔티티를 응답 DTO로 변환
    private PostResponse toPostResponse(Post post, String authorUsername) {
        String username = authorUsername;
        if (username == null) {
            username = userRepository.findById(post.getUserId())
                    .map(User::getUsername)
                    .orElse("unknown");
        }
        return new PostResponse(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                post.getFeedbackType(),
                post.getGraphId(),
                post.getUserId(),
                username,
                post.getCreatedAt()
        );
    }

    // Comment 엔티티를 응답 DTO로 변환
    private CommentResponse toCommentResponse(Comment comment) {
        String username = userRepository.findById(comment.getUserId())
                .map(User::getUsername)
                .orElse("unknown");
        return new CommentResponse(
                comment.getId(),
                comment.getContent(),
                comment.getUserId(),
                username,
                comment.getCreatedAt()
        );
    }

    // 게시글 생성 요청 DTO
    public record CreatePostRequest(String title, String content, String feedbackType, UUID graphId) {}

    // 댓글 생성 요청 DTO
    public record CreateCommentRequest(String content) {}

    // 게시글 응답 DTO
    public record PostResponse(
            UUID id, String title, String content, String feedbackType,
            UUID graphId, UUID userId, String authorUsername, Instant createdAt) {}

    // 댓글 응답 DTO
    public record CommentResponse(
            UUID id, String content, UUID userId, String authorUsername, Instant createdAt) {}

    // 게시글 + 댓글 목록 응답 DTO
    public record PostDetailResponse(PostResponse post, List<CommentResponse> comments) {}
}
