// 커뮤니티 게시글/댓글/북마크 REST API 컨트롤러
package com.codeprint.interfaces.api;

import com.codeprint.application.community.PostCommandService;
import com.codeprint.application.graph.GraphQueryService;
import com.codeprint.application.notification.NotificationService;
import com.codeprint.domain.community.Comment;
import com.codeprint.domain.community.Post;
import com.codeprint.domain.community.PostBookmark;
import com.codeprint.domain.community.PostBookmarkRepository;
import com.codeprint.domain.community.PostLike;
import com.codeprint.domain.community.PostLikeRepository;
import com.codeprint.domain.community.PostRepository;
import com.codeprint.domain.graph.Edge;
import com.codeprint.domain.graph.Node;
import com.codeprint.domain.user.User;
import com.codeprint.domain.user.UserFollowRepository;
import com.codeprint.domain.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/community")
@RequiredArgsConstructor
public class CommunityController {

    private final PostCommandService postCommandService;
    private final GraphQueryService graphQueryService;
    private final UserRepository userRepository;
    private final PostBookmarkRepository bookmarkRepository;
    private final PostLikeRepository likeRepository;
    private final PostRepository postRepository;
    private final UserFollowRepository followRepository;
    private final NotificationService notificationService;

    // 게시글 목록 조회 (페이지, 검색, 팔로잉 피드) — 로그인 시 내 북마크 여부 포함
    @GetMapping("/posts")
    public ResponseEntity<List<PostResponse>> getPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String feed,
            @AuthenticationPrincipal User user) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        List<Post> raw;
        if (q != null && !q.isBlank()) {
            raw = postRepository.findByTitleContainingIgnoreCaseOrContentContainingIgnoreCaseOrderByCreatedAtDesc(q, q, pageable);
        } else if ("following".equals(feed) && user != null) {
            List<java.util.UUID> followingIds = followRepository.findByFollowerId(user.getId()).stream()
                    .map(f -> f.getFollowingId())
                    .toList();
            raw = followingIds.isEmpty()
                    ? List.of()
                    : postRepository.findByUserIdInOrderByCreatedAtDesc(followingIds, pageable);
        } else {
            raw = postCommandService.getPosts(page, size);
        }
        List<PostResponse> posts = raw.stream()
                .map(p -> toPostResponse(p, null, user))
                .toList();
        return ResponseEntity.ok(posts);
    }

    // 게시글 단건 + 댓글 + 첨부파일 목록 조회
    @GetMapping("/posts/{postId}")
    public ResponseEntity<PostDetailResponse> getPost(
            @PathVariable UUID postId,
            @AuthenticationPrincipal User user) {
        Post post = postCommandService.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found: " + postId));
        List<CommentResponse> comments = postCommandService.getComments(postId).stream()
                .map(this::toCommentResponse)
                .toList();
        List<AttachmentResponse> attachments = postCommandService.getAttachmentsWithUrls(postId).stream()
                .map(a -> new AttachmentResponse(a.id(), a.originalFilename(), a.contentType(), a.url()))
                .toList();
        return ResponseEntity.ok(new PostDetailResponse(toPostResponse(post, null, user), comments, attachments));
    }

    // 새 게시글 작성 (첨부파일 메타데이터 포함)
    @PostMapping("/posts")
    public ResponseEntity<PostResponse> createPost(
            @Valid @RequestBody CreatePostRequest request,
            @AuthenticationPrincipal User user) {
        Post post = postCommandService.createPost(
                user.getId(),
                request.graphId(),
                request.title(),
                request.content(),
                request.feedbackType(),
                request.hiddenLayers(),
                request.hiddenGroups(),
                request.hiddenNodeNames());
        if (request.attachments() != null && !request.attachments().isEmpty()) {
            List<PostCommandService.AttachmentInfo> infos = request.attachments().stream()
                    .map(a -> new PostCommandService.AttachmentInfo(a.s3Key(), a.originalFilename(), a.contentType()))
                    .toList();
            postCommandService.saveAttachments(post.getId(), infos);
        }
        return ResponseEntity.status(201).body(toPostResponse(post, user.getUsername(), user));
    }

    // 게시글에 첨부된 그래프를 숨김 필터 적용하여 반환
    @GetMapping("/posts/{postId}/graph")
    public ResponseEntity<?> getPostGraph(@PathVariable UUID postId) {
        Post post = postCommandService.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found: " + postId));
        if (post.getGraphId() == null) return ResponseEntity.notFound().build();

        return graphQueryService.findById(post.getGraphId())
                .map(graph -> {
                    List<Node> nodes = graphQueryService.getNodes(graph.getId());
                    List<Edge> edges = graphQueryService.getEdges(graph.getId());

                    List<Map<String, Object>> nodeData = nodes.stream()
                            .filter(n -> !n.isHidden())
                            .map(n -> {
                                Map<String, Object> node = new java.util.LinkedHashMap<>();
                                node.put("id", n.getId().toString());
                                node.put("type", n.getType().name());
                                node.put("name", n.getName());
                                node.put("filePath", n.getFilePath() != null ? n.getFilePath() : "");
                                node.put("language", n.getLanguage() != null ? n.getLanguage() : "");
                                node.put("posX", n.getPosX());
                                node.put("posY", n.getPosY());
                                if (n.getMetadata() != null && n.getMetadata().containsKey("comment")) {
                                    node.put("comment", n.getMetadata().get("comment"));
                                }
                                return node;
                            })
                            .toList();

                    List<Map<String, Object>> edgeData = edges.stream()
                            .filter(e -> !e.isHidden())
                            .map(e -> Map.<String, Object>of(
                                    "id", e.getId().toString(),
                                    "type", e.getType().name(),
                                    "source", e.getSourceNodeId().toString(),
                                    "target", e.getTargetNodeId().toString(),
                                    "edgeIdentifier", e.getEdgeIdentifier()
                            ))
                            .toList();

                    return ResponseEntity.ok(Map.of(
                            "graphId", graph.getId().toString(),
                            "nodes", nodeData,
                            "edges", edgeData,
                            "hiddenLayers", post.getHiddenLayers(),
                            "hiddenGroups", post.getHiddenGroups(),
                            "hiddenNodeNames", post.getHiddenNodeNames()
                    ));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // 댓글 작성 — 게시글 작성자에게 알림 발송
    @PostMapping("/posts/{postId}/comments")
    public ResponseEntity<CommentResponse> addComment(
            @PathVariable UUID postId,
            @Valid @RequestBody CreateCommentRequest request,
            @AuthenticationPrincipal User user) {
        Comment comment = postCommandService.addComment(postId, user.getId(), request.content());
        postRepository.findById(postId).ifPresent(post -> {
            if (!post.getUserId().equals(user.getId())) {
                notificationService.create(post.getUserId(), "COMMENT",
                        user.getUsername() + "님이 댓글을 달았습니다.", "/community?postId=" + postId);
            }
        });
        return ResponseEntity.status(201).body(toCommentResponse(comment));
    }

    // 댓글 삭제 (작성자 본인만)
    @DeleteMapping("/posts/{postId}/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @PathVariable UUID postId,
            @PathVariable UUID commentId,
            @AuthenticationPrincipal User user) {
        postCommandService.deleteComment(commentId, user.getId());
        return ResponseEntity.noContent().build();
    }

    // 게시글 삭제
    @DeleteMapping("/posts/{postId}")
    public ResponseEntity<Void> deletePost(
            @PathVariable UUID postId,
            @AuthenticationPrincipal User user) {
        postCommandService.deletePost(postId, user.getId());
        return ResponseEntity.noContent().build();
    }

    // 게시글 북마크 추가
    @PostMapping("/posts/{postId}/bookmark")
    public ResponseEntity<Void> addBookmark(
            @PathVariable UUID postId,
            @AuthenticationPrincipal User user) {
        if (!bookmarkRepository.existsByUserIdAndPostId(user.getId(), postId)) {
            bookmarkRepository.save(PostBookmark.of(user.getId(), postId));
        }
        return ResponseEntity.ok().build();
    }

    // 게시글 북마크 취소
    @DeleteMapping("/posts/{postId}/bookmark")
    public ResponseEntity<Void> removeBookmark(
            @PathVariable UUID postId,
            @AuthenticationPrincipal User user) {
        bookmarkRepository.deleteByUserIdAndPostId(user.getId(), postId);
        return ResponseEntity.noContent().build();
    }

    // 게시글 좋아요 추가
    @PostMapping("/posts/{postId}/like")
    public ResponseEntity<Void> addLike(
            @PathVariable UUID postId,
            @AuthenticationPrincipal User user) {
        if (!likeRepository.existsByUserIdAndPostId(user.getId(), postId)) {
            likeRepository.save(PostLike.of(user.getId(), postId));
            postRepository.findById(postId).ifPresent(post -> {
                if (!post.getUserId().equals(user.getId())) {
                    notificationService.create(post.getUserId(), "LIKE",
                            user.getUsername() + "님이 게시글을 좋아합니다.", "/community?postId=" + postId);
                }
            });
        }
        return ResponseEntity.ok().build();
    }

    // 게시글 좋아요 취소
    @DeleteMapping("/posts/{postId}/like")
    public ResponseEntity<Void> removeLike(
            @PathVariable UUID postId,
            @AuthenticationPrincipal User user) {
        likeRepository.deleteByUserIdAndPostId(user.getId(), postId);
        return ResponseEntity.noContent().build();
    }

    // 내 북마크 목록 조회 (최신순, 최대 50개)
    @GetMapping("/bookmarks")
    public ResponseEntity<List<PostResponse>> getMyBookmarks(@AuthenticationPrincipal User user) {
        List<PostResponse> posts = bookmarkRepository
                .findByUserIdOrderByCreatedAtDesc(user.getId(), 50).stream()
                .flatMap(bm -> postRepository.findById(bm.getPostId()).stream())
                .map(p -> toPostResponse(p, null, user))
                .toList();
        return ResponseEntity.ok(posts);
    }

    // Post 엔티티를 응답 DTO로 변환 (북마크/좋아요 수 및 내 여부 포함)
    private PostResponse toPostResponse(Post post, String authorUsername, User currentUser) {
        String username = authorUsername;
        if (username == null) {
            username = userRepository.findById(post.getUserId())
                    .map(User::getUsername)
                    .orElse("unknown");
        }
        long bookmarkCount = bookmarkRepository.countByPostId(post.getId());
        boolean bookmarkedByMe = currentUser != null &&
                bookmarkRepository.existsByUserIdAndPostId(currentUser.getId(), post.getId());
        long likeCount = likeRepository.countByPostId(post.getId());
        boolean likedByMe = currentUser != null &&
                likeRepository.existsByUserIdAndPostId(currentUser.getId(), post.getId());
        return new PostResponse(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                post.getFeedbackType(),
                post.getGraphId(),
                post.getUserId(),
                username,
                post.getCreatedAt(),
                bookmarkCount,
                bookmarkedByMe,
                likeCount,
                likedByMe
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
    public record CreatePostRequest(
            @NotBlank String title, String content, String feedbackType, UUID graphId,
            List<String> hiddenLayers, List<String> hiddenGroups, List<String> hiddenNodeNames,
            List<AttachmentRequest> attachments) {}

    // 첨부파일 요청 DTO
    public record AttachmentRequest(String s3Key, String originalFilename, String contentType) {}

    // 첨부파일 응답 DTO
    public record AttachmentResponse(UUID id, String originalFilename, String contentType, String url) {}

    // 댓글 생성 요청 DTO
    public record CreateCommentRequest(@NotBlank String content) {}

    // 게시글 응답 DTO
    public record PostResponse(
            UUID id, String title, String content, String feedbackType,
            UUID graphId, UUID userId, String authorUsername, Instant createdAt,
            long bookmarkCount, boolean bookmarkedByMe,
            long likeCount, boolean likedByMe) {}

    // 댓글 응답 DTO
    public record CommentResponse(
            UUID id, String content, UUID userId, String authorUsername, Instant createdAt) {}

    // 게시글 + 댓글 + 첨부파일 목록 응답 DTO
    public record PostDetailResponse(PostResponse post, List<CommentResponse> comments,
                                     List<AttachmentResponse> attachments) {}
}
