// 커뮤니티 게시글/댓글/북마크 REST API 컨트롤러
package com.codeprint.interfaces.api;

import com.codeprint.application.community.CommunityFacade;
import com.codeprint.application.community.PostCommandService;
import com.codeprint.shared.event.CommentAddedEvent;
import com.codeprint.domain.community.Comment;
import com.codeprint.domain.community.Post;
import com.codeprint.shared.event.PostLikedEvent;
import com.codeprint.domain.community.PostBookmark;
import com.codeprint.domain.community.PostBookmarkRepository;
import com.codeprint.domain.community.PostLike;
import com.codeprint.domain.community.PostLikeRepository;
import com.codeprint.domain.community.PostGraphSnapshot;
import com.codeprint.domain.community.PostRepository;
import com.codeprint.domain.community.port.GraphReadPort;
import com.codeprint.domain.user.User;
import com.codeprint.domain.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/community")
@RequiredArgsConstructor
public class CommunityController {

    private final PostCommandService postCommandService;
    private final CommunityFacade communityFacade;
    private final ApplicationEventPublisher eventPublisher;
    private final UserRepository userRepository;
    private final PostBookmarkRepository bookmarkRepository;
    private final PostLikeRepository likeRepository;
    private final PostRepository postRepository;

    // 게시글 목록 조회 (페이지, 검색, 팔로잉 피드) — 로그인 시 내 북마크 여부 포함
    @GetMapping("/posts")
    public ResponseEntity<List<PostResponse>> getPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String feed,
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(defaultValue = "false") boolean graphOnly,
            @AuthenticationPrincipal User user) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        List<Post> raw;
        if (graphOnly) {
            raw = postRepository.findWithGraphOrSnapshots(pageable);
        } else if (q != null && !q.isBlank()) {
            raw = postRepository.findByTitleContainingIgnoreCaseOrContentContainingIgnoreCaseOrderByCreatedAtDesc(q, q, pageable);
        } else if ("following".equals(feed) && user != null) {
            raw = communityFacade.getFollowingFeed(user.getId(), pageable);
        } else if ("likes".equals(sort)) {
            raw = postRepository.findAllOrderByLikeCountDesc(pageable);
        } else if ("views".equals(sort)) {
            raw = postRepository.findAllByOrderByViewCountDesc(pageable);
        } else {
            raw = postCommandService.getPosts(page, size);
        }
        return ResponseEntity.ok(toPostResponses(filterVisible(raw, user), user));
    }

    // 비공개 게시글은 목록에서 제외 — 작성자 본인에게만 예외적으로 보임(직접 링크로만 접근 가능해야 함)
    private List<Post> filterVisible(List<Post> posts, User user) {
        return posts.stream()
                .filter(p -> p.isPublic() || (user != null && p.getUserId().equals(user.getId())))
                .toList();
    }

    // 게시글 단건 + 댓글 + 첨부파일 목록 조회
    @GetMapping("/posts/{postId}")
    public ResponseEntity<PostDetailResponse> getPost(
            @PathVariable UUID postId,
            @AuthenticationPrincipal User user) {
        Post post = postCommandService.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found: " + postId));
        post.incrementViewCount();
        postRepository.save(post);
        List<CommentResponse> comments = toCommentResponses(postCommandService.getComments(postId));
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
        // 공개 프로젝트이면 레포 URL 포함 — 프라이빗 프로젝트는 null 처리
        String repoUrl = null;
        if (request.graphId() != null) {
            repoUrl = communityFacade.findPublicRepoUrl(request.graphId(), user.getId()).orElse(null);
        }
        Post post = postCommandService.createPost(
                user.getId(),
                request.graphId(),
                request.title(),
                request.content(),
                request.feedbackType(),
                request.hiddenLayers(),
                request.hiddenGroups(),
                request.hiddenNodeNames(),
                repoUrl);
        if (request.attachments() != null && !request.attachments().isEmpty()) {
            List<PostCommandService.AttachmentInfo> infos = request.attachments().stream()
                    .map(a -> new PostCommandService.AttachmentInfo(a.s3Key(), a.originalFilename(), a.contentType()))
                    .toList();
            postCommandService.saveAttachments(post.getId(), infos);
        }
        if (request.graphSnapshots() != null && !request.graphSnapshots().isEmpty()) {
            List<PostCommandService.SnapshotToSave> toSave = new java.util.ArrayList<>();
            for (GraphSnapshotRequest spec : request.graphSnapshots()) {
                communityFacade.captureGraphSnapshot(spec.projectId(), user.getId(), spec.presetSlot())
                        .ifPresent(snap -> toSave.add(new PostCommandService.SnapshotToSave(
                                spec.projectId(), snap.graphId(), snap.config())));
            }
            postCommandService.saveGraphSnapshots(post.getId(), toSave);
        }
        if ("PRIVATE".equals(request.visibility())) {
            post = postCommandService.makePrivate(post.getId());
        }
        return ResponseEntity.status(201).body(toPostResponse(post, user.getUsername(), user));
    }

    // 게시글에 첨부된 그래프를 숨김 필터 적용하여 반환 (레거시 단일 첨부 전용 — 다중 스냅샷은 /snapshots)
    // permitAll 엔드포인트라 숨김 필터는 반드시 서버에서 적용한다 — 프론트(CommunityPostGraphPage.tsx)도 동일 필터를
    // 한 번 더 적용하지만, 그건 표시 레이어 방어일 뿐이고 실제 노출 차단은 여기서 이뤄져야 한다.
    @GetMapping("/posts/{postId}/graph")
    public ResponseEntity<?> getPostGraph(@PathVariable UUID postId) {
        Post post = postCommandService.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found: " + postId));
        if (post.getGraphId() == null) return ResponseEntity.notFound().build();

        return communityFacade.getGraphSnapshot(post.getGraphId())
                .map(snapshot -> {
                    List<GraphReadPort.NodeView> allowedNodes = applyPostHiddenFilter(
                            snapshot.nodes(), post.getHiddenLayers(), post.getHiddenGroups(), post.getHiddenNodeNames());
                    Set<UUID> allowedIds = allowedNodes.stream().map(GraphReadPort.NodeView::id).collect(Collectors.toSet());
                    List<GraphReadPort.EdgeView> allowedEdges = snapshot.edges().stream()
                            .filter(e -> allowedIds.contains(e.source()) && allowedIds.contains(e.target()))
                            .toList();
                    return ResponseEntity.ok(Map.of(
                            "graphId", snapshot.graphId().toString(),
                            "nodes", toNodeMaps(allowedNodes),
                            "edges", toEdgeMaps(allowedEdges),
                            "hiddenLayers", post.getHiddenLayers(),
                            "hiddenGroups", post.getHiddenGroups(),
                            "hiddenNodeNames", post.getHiddenNodeNames()
                    ));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // DDD 레이어 폴더명 — 프론트 CommunityPostGraphPage.tsx의 DDD_LAYERS와 반드시 동일하게 유지
    private static final List<String> DDD_LAYERS = List.of(
            "domain", "application", "infrastructure", "interfaces", "pages", "components", "hooks", "utils");

    // 그룹 키 계산 시 건너뛰는 의미 없는 래퍼 디렉터리 — 프론트 graphLayout.ts의 NON_SEMANTIC_WRAPPER_DIRS와 동일
    private static final Set<String> NON_SEMANTIC_WRAPPER_DIRS = Set.of("src", "lib", "crates", "packages", "pkg");

    // 게시글 작성 시 선택한 hiddenLayers/hiddenGroups/hiddenNodeNames를 실제로 적용해 노드를 제외 —
    // 프론트 CommunityPostGraphPage.tsx의 applyHiddenFilter와 동일 알고리즘(서버가 응답 자체에서 제외해야 의미가 있음)
    static List<GraphReadPort.NodeView> applyPostHiddenFilter(List<GraphReadPort.NodeView> nodes,
                                                                       List<String> hiddenLayers,
                                                                       List<String> hiddenGroups,
                                                                       List<String> hiddenNodeNames) {
        List<String> filePaths = nodes.stream()
                .filter(n -> !n.hidden() && "FILE".equals(n.type()) && n.filePath() != null)
                .map(GraphReadPort.NodeView::filePath)
                .toList();
        String commonPrefix = findCommonPrefix(filePaths);
        return nodes.stream()
                .filter(n -> !n.hidden())
                .filter(n -> {
                    if (hiddenNodeNames.contains(n.name())) return false;
                    if (n.filePath() != null && !n.filePath().isBlank()) {
                        if (hiddenLayers.contains(getLayer(n.filePath()))) return false;
                        if (hiddenGroups.contains(getGroupKey(n.filePath(), commonPrefix))) return false;
                    }
                    return true;
                })
                .toList();
    }

    // filePath에서 DDD 레이어 이름 추출 — 프론트 CommunityPostGraphPage.tsx getLayer와 동일 알고리즘
    private static String getLayer(String filePath) {
        for (String part : filePath.replace("\\", "/").split("/")) {
            if (DDD_LAYERS.contains(part)) return part;
        }
        return "root";
    }

    // filePath 목록의 공통 prefix 계산 — 프론트 graphLayout.ts findCommonPrefix와 동일 알고리즘
    private static String findCommonPrefix(List<String> paths) {
        if (paths.isEmpty()) return "";
        String[] parts = paths.get(0).replace("\\", "/").split("/");
        String prefix = "";
        for (int depth = 1; depth <= parts.length; depth++) {
            String candidate = String.join("/", java.util.Arrays.asList(parts).subList(0, depth)) + "/";
            String finalCandidate = candidate;
            if (paths.stream().allMatch(p -> p.replace("\\", "/").startsWith(finalCandidate))) {
                prefix = candidate;
            } else {
                break;
            }
        }
        return prefix;
    }

    // filePath에서 그룹 키 추출 — 프론트 graphLayout.ts getGroupKey와 동일 알고리즘
    private static String getGroupKey(String filePath, String commonPrefix) {
        String rel = filePath.startsWith(commonPrefix) ? filePath.substring(commonPrefix.length()) : filePath;
        List<String> parts = java.util.Arrays.stream(rel.replace("\\", "/").split("/"))
                .filter(s -> !s.isEmpty())
                .toList();
        for (int i = 0; i < parts.size(); i++) {
            if (DDD_LAYERS.contains(parts.get(i))) {
                return (i + 1 < parts.size()) ? parts.get(i) + "/" + parts.get(i + 1) : parts.get(i);
            }
        }
        List<String> dirParts = parts.isEmpty() ? List.of() : parts.subList(0, parts.size() - 1);
        int start = 0;
        while (start < dirParts.size() && NON_SEMANTIC_WRAPPER_DIRS.contains(dirParts.get(start))) start++;
        return start < dirParts.size() ? dirParts.get(start) : "root";
    }

    // 게시글에 첨부된 그래프 스냅샷 목록 조회 (신규 다중 스냅샷 — 각각 캡처 시점 config 포함)
    @GetMapping("/posts/{postId}/snapshots")
    public ResponseEntity<List<Map<String, Object>>> getPostSnapshots(@PathVariable UUID postId) {
        postCommandService.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found: " + postId));
        List<Map<String, Object>> result = postCommandService.getGraphSnapshots(postId).stream()
                .map(this::toSnapshotResponse)
                .filter(java.util.Objects::nonNull)
                .toList();
        return ResponseEntity.ok(result);
    }

    // 스냅샷 하나를 노드·엣지·캡처된 config 포함 응답 Map으로 변환 (그래프 소실 시 null)
    private Map<String, Object> toSnapshotResponse(PostGraphSnapshot snapshot) {
        return communityFacade.getGraphSnapshot(snapshot.getGraphId())
                .<Map<String, Object>>map(gs -> {
                    Map<String, Object> map = new java.util.LinkedHashMap<>();
                    map.put("projectId", snapshot.getProjectId().toString());
                    map.put("graphId", gs.graphId().toString());
                    map.put("nodes", toNodeMaps(gs.nodes()));
                    map.put("edges", toEdgeMaps(gs.edges()));
                    map.put("config", snapshot.getConfig());
                    map.put("position", snapshot.getPosition());
                    map.put("warnings", communityFacade.getActiveWarnings(gs.graphId()));
                    return map;
                })
                .orElse(null);
    }

    // 그래프 노드 뷰 목록 → 프론트 응답 Map 목록 변환 (숨김 노드 제외) — /graph, /snapshots 공용
    static List<Map<String, Object>> toNodeMaps(List<GraphReadPort.NodeView> nodes) {
        return nodes.stream()
                .filter(n -> !n.hidden())
                .map(n -> {
                    Map<String, Object> node = new java.util.LinkedHashMap<>();
                    node.put("id", n.id().toString());
                    node.put("type", n.type());
                    node.put("name", n.name());
                    node.put("filePath", n.filePath() != null ? n.filePath() : "");
                    node.put("language", n.language() != null ? n.language() : "");
                    node.put("posX", n.posX());
                    node.put("posY", n.posY());
                    if (n.comment() != null) {
                        node.put("comment", n.comment());
                    }
                    return node;
                })
                .toList();
    }

    // 그래프 엣지 뷰 목록 → 프론트 응답 Map 목록 변환 (숨김 엣지 제외) — /graph, /snapshots 공용
    static List<Map<String, Object>> toEdgeMaps(List<GraphReadPort.EdgeView> edges) {
        return edges.stream()
                .filter(e -> !e.hidden())
                .map(e -> Map.<String, Object>of(
                        "id", e.id().toString(),
                        "type", e.type(),
                        "source", e.source().toString(),
                        "target", e.target().toString(),
                        "edgeIdentifier", e.edgeIdentifier()
                ))
                .toList();
    }

    // 댓글 작성 — 게시글 작성자에게 알림 발송
    @PostMapping("/posts/{postId}/comments")
    public ResponseEntity<CommentResponse> addComment(
            @PathVariable UUID postId,
            @Valid @RequestBody CreateCommentRequest request,
            @AuthenticationPrincipal User user) {
        Comment comment = postCommandService.addComment(postId, user.getId(), request.content());
        postRepository.findById(postId).ifPresent(post ->
                eventPublisher.publishEvent(
                        new CommentAddedEvent(postId, post.getUserId(), user.getId(), user.getUsername())));
        return ResponseEntity.status(201).body(toCommentResponse(comment));
    }

    // 게시글 수정 (작성자 본인만)
    @PutMapping("/posts/{postId}")
    public ResponseEntity<PostResponse> updatePost(
            @PathVariable UUID postId,
            @Valid @RequestBody UpdatePostRequest request,
            @AuthenticationPrincipal User user) {
        Post post = postCommandService.updatePost(postId, user.getId(), request.title(), request.content());
        return ResponseEntity.ok(toPostResponse(post, user.getUsername(), user));
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
            postRepository.findById(postId).ifPresent(post ->
                    eventPublisher.publishEvent(
                            new PostLikedEvent(postId, post.getUserId(), user.getId(), user.getUsername())));
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
        List<Post> posts = bookmarkRepository
                .findByUserIdOrderByCreatedAtDesc(user.getId(), 50).stream()
                .flatMap(bm -> postRepository.findById(bm.getPostId()).stream())
                .toList();
        return ResponseEntity.ok(toPostResponses(posts, user));
    }

    // 게시글 목록을 응답 DTO 목록으로 일괄 변환 — 작성자명/카운트/내 여부를 페이지 단위 배치 조회해 N+1 제거
    private List<PostResponse> toPostResponses(List<Post> posts, User currentUser) {
        if (posts.isEmpty()) return List.of();
        List<UUID> postIds = posts.stream().map(Post::getId).toList();
        List<UUID> userIds = posts.stream().map(Post::getUserId).distinct().toList();

        Map<UUID, String> usernames = new HashMap<>();
        for (User u : userRepository.findByIdIn(userIds)) usernames.put(u.getId(), u.getUsername());

        Map<UUID, Long> bookmarkCounts = toCountMap(bookmarkRepository.countByPostIdIn(postIds));
        Map<UUID, Long> likeCounts = toCountMap(likeRepository.countByPostIdIn(postIds));
        Map<UUID, Long> commentCounts = toCountMap(postRepository.countCommentsByPostIdIn(postIds));

        Set<UUID> myBookmarks = currentUser == null ? Set.of()
                : bookmarkRepository.findByUserIdAndPostIdIn(currentUser.getId(), postIds).stream()
                        .map(PostBookmark::getPostId).collect(Collectors.toSet());
        Set<UUID> myLikes = currentUser == null ? Set.of()
                : likeRepository.findByUserIdAndPostIdIn(currentUser.getId(), postIds).stream()
                        .map(PostLike::getPostId).collect(Collectors.toSet());
        Set<UUID> postsWithSnapshots = new HashSet<>(postRepository.findPostIdsWithSnapshots(postIds));

        return assemble(posts, usernames, bookmarkCounts, likeCounts, commentCounts, myBookmarks, myLikes, postsWithSnapshots);
    }

    // [postId, count] 행 목록을 postId→count Map으로 변환 (없는 글은 호출측에서 0 기본값)
    static Map<UUID, Long> toCountMap(List<Object[]> rows) {
        Map<UUID, Long> map = new HashMap<>();
        for (Object[] row : rows) map.put((UUID) row[0], ((Number) row[1]).longValue());
        return map;
    }

    // 배치 조회한 메타데이터로 PostResponse 목록 조립 (순수 함수 — 카운트 없는 글은 0, 내 여부는 set 포함 여부)
    static List<PostResponse> assemble(List<Post> posts,
                                       Map<UUID, String> usernames,
                                       Map<UUID, Long> bookmarkCounts,
                                       Map<UUID, Long> likeCounts,
                                       Map<UUID, Long> commentCounts,
                                       Set<UUID> myBookmarks,
                                       Set<UUID> myLikes,
                                       Set<UUID> postsWithSnapshots) {
        return posts.stream().map(p -> new PostResponse(
                p.getId(),
                p.getTitle(),
                p.getContent(),
                p.getFeedbackType(),
                p.getGraphId(),
                p.getUserId(),
                usernames.getOrDefault(p.getUserId(), "unknown"),
                p.getCreatedAt(),
                bookmarkCounts.getOrDefault(p.getId(), 0L),
                myBookmarks.contains(p.getId()),
                likeCounts.getOrDefault(p.getId(), 0L),
                myLikes.contains(p.getId()),
                p.getViewCount(),
                commentCounts.getOrDefault(p.getId(), 0L),
                p.getRepoUrl(),
                p.getGraphId() != null || postsWithSnapshots.contains(p.getId()),
                com.codeprint.shared.GithubRepoOwner.matches(p.getRepoUrl(), usernames.get(p.getUserId()))
        )).toList();
    }

    // Post 엔티티를 응답 DTO로 변환 (북마크/좋아요 수 및 내 여부 포함) — 단건(게시글 상세)용
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
        long commentCount = postRepository.countCommentsByPostId(post.getId());
        boolean hasGraph = post.getGraphId() != null || !postRepository.findSnapshotsByPostId(post.getId()).isEmpty();
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
                likedByMe,
                post.getViewCount(),
                commentCount,
                post.getRepoUrl(),
                hasGraph,
                com.codeprint.shared.GithubRepoOwner.matches(post.getRepoUrl(), username)
        );
    }

    // 댓글 목록을 응답 DTO 목록으로 일괄 변환 — 작성자명을 한 번에 배치 조회해 N+1 제거
    private List<CommentResponse> toCommentResponses(List<Comment> comments) {
        if (comments.isEmpty()) return List.of();
        List<UUID> userIds = comments.stream().map(Comment::getUserId).distinct().toList();
        Map<UUID, String> usernames = new HashMap<>();
        for (User u : userRepository.findByIdIn(userIds)) usernames.put(u.getId(), u.getUsername());
        return comments.stream().map(c -> new CommentResponse(
                c.getId(),
                c.getContent(),
                c.getUserId(),
                usernames.getOrDefault(c.getUserId(), "unknown"),
                c.getCreatedAt()
        )).toList();
    }

    // Comment 엔티티를 응답 DTO로 변환 (단건 — 댓글 작성 응답)
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

    // 게시글 수정 요청 DTO
    public record UpdatePostRequest(@NotBlank @Size(max = 300) String title, @Size(max = 20000) String content) {}

    // 게시글 생성 요청 DTO — title/content 길이는 각각 DB posts.title(length=300)·저장소 남용 방지 상한과 정합
    public record CreatePostRequest(
            @NotBlank @Size(max = 300) String title, @Size(max = 20000) String content, String feedbackType, UUID graphId,
            List<String> hiddenLayers, List<String> hiddenGroups, List<String> hiddenNodeNames,
            List<AttachmentRequest> attachments,
            @Valid List<GraphSnapshotRequest> graphSnapshots, String visibility) {}

    // 첨부파일 요청 DTO
    public record AttachmentRequest(String s3Key, String originalFilename, String contentType) {}

    // 그래프 스냅샷 첨부 요청 DTO — 프로젝트+프리셋 슬롯을 지정하면 그 순간의 설정을 캡처
    public record GraphSnapshotRequest(UUID projectId, @Min(1) @Max(4) int presetSlot) {}

    // 첨부파일 응답 DTO
    public record AttachmentResponse(UUID id, String originalFilename, String contentType, String url) {}

    // 댓글 생성 요청 DTO
    public record CreateCommentRequest(@NotBlank @Size(max = 2000) String content) {}

    // 게시글 응답 DTO
    public record PostResponse(
            UUID id, String title, String content, String feedbackType,
            UUID graphId, UUID userId, String authorUsername, Instant createdAt,
            long bookmarkCount, boolean bookmarkedByMe,
            long likeCount, boolean likedByMe,
            long viewCount, long commentCount, String repoUrl, boolean hasGraph, boolean ownRepo) {}

    // 댓글 응답 DTO
    public record CommentResponse(
            UUID id, String content, UUID userId, String authorUsername, Instant createdAt) {}

    // 게시글 + 댓글 + 첨부파일 목록 응답 DTO
    public record PostDetailResponse(PostResponse post, List<CommentResponse> comments,
                                     List<AttachmentResponse> attachments) {}
}
