// 공개 유저 프로필 조회 REST API
package com.codeprint.interfaces.api;

import com.codeprint.domain.community.Post;
import com.codeprint.domain.community.PostBookmark;
import com.codeprint.domain.community.PostBookmarkRepository;
import com.codeprint.domain.community.PostRepository;
import com.codeprint.domain.project.Project;
import com.codeprint.domain.project.ProjectRepository;
import com.codeprint.domain.user.User;
import com.codeprint.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final PostBookmarkRepository bookmarkRepository;
    private final ProjectRepository projectRepository;

    // 사용자명 키워드로 유저 검색 (최대 10명)
    @GetMapping
    public ResponseEntity<List<UserProfileResponse>> searchUsers(@RequestParam(required = false) String q) {
        if (q == null || q.isBlank()) return ResponseEntity.ok(List.of());
        List<UserProfileResponse> results = userRepository.searchByUsername(q.trim()).stream()
                .map(this::toProfile)
                .toList();
        return ResponseEntity.ok(results);
    }

    // 공개 유저 프로필 조회
    @GetMapping("/{userId}")
    public ResponseEntity<UserProfileResponse> getProfile(@PathVariable UUID userId) {
        return userRepository.findById(userId)
                .map(u -> ResponseEntity.ok(toProfile(u)))
                .orElse(ResponseEntity.notFound().build());
    }

    // 유저가 작성한 게시글 목록 조회 (최신순)
    @GetMapping("/{userId}/posts")
    public ResponseEntity<List<PostSummaryResponse>> getUserPosts(
            @PathVariable UUID userId,
            @AuthenticationPrincipal User currentUser) {
        // 비공개 게시글은 목록에서 제외 — 작성자 본인이 자기 프로필을 볼 때만 예외적으로 보임
        List<Post> posts = postRepository.findByUserId(userId).stream()
                .filter(p -> p.isPublic() || (currentUser != null && currentUser.getId().equals(userId)))
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .toList();
        String profileUsername = userRepository.findById(userId).map(User::getUsername).orElse(null);
        return ResponseEntity.ok(toPostSummaries(posts, currentUser, profileUsername));
    }

    // 유저의 공개 프로젝트 목록 조회
    @GetMapping("/{userId}/projects")
    public ResponseEntity<List<PublicProjectResponse>> getUserProjects(@PathVariable UUID userId) {
        List<PublicProjectResponse> projects = projectRepository.findPublicByUserId(userId).stream()
                .map(p -> new PublicProjectResponse(p.getId(), p.getName(), p.getDescription(), p.getCreatedAt()))
                .toList();
        return ResponseEntity.ok(projects);
    }

    // User → UserProfileResponse 변환
    private UserProfileResponse toProfile(User u) {
        String avatarUrl = "https://github.com/" + u.getUsername() + ".png";
        return new UserProfileResponse(u.getId(), u.getUsername(), avatarUrl, u.getCreatedAt());
    }

    // 게시글 목록을 요약 DTO 목록으로 일괄 변환 — 북마크 수/내 여부를 페이지 단위 배치 조회해 N+1 제거
    private List<PostSummaryResponse> toPostSummaries(List<Post> posts, User currentUser, String profileUsername) {
        if (posts.isEmpty()) return List.of();
        List<UUID> postIds = posts.stream().map(Post::getId).toList();

        Map<UUID, Long> bookmarkCounts = new HashMap<>();
        for (Object[] row : bookmarkRepository.countByPostIdIn(postIds)) {
            bookmarkCounts.put((UUID) row[0], ((Number) row[1]).longValue());
        }
        Set<UUID> myBookmarks = currentUser == null ? Set.of()
                : bookmarkRepository.findByUserIdAndPostIdIn(currentUser.getId(), postIds).stream()
                        .map(PostBookmark::getPostId).collect(Collectors.toSet());
        Set<UUID> postsWithSnapshots = new HashSet<>(postRepository.findPostIdsWithSnapshots(postIds));

        return assembleSummaries(posts, bookmarkCounts, myBookmarks, postsWithSnapshots, profileUsername);
    }

    // 배치 조회한 북마크 메타로 요약 DTO 목록 조립 (순수 함수 — 카운트 없는 글은 0)
    // profileUsername: 이 목록의 작성자(프로필 주인)는 전부 동일 인물이라 배치 조회 불필요, 단일 값 비교로 충분
    static List<PostSummaryResponse> assembleSummaries(List<Post> posts,
                                                       Map<UUID, Long> bookmarkCounts,
                                                       Set<UUID> myBookmarks,
                                                       Set<UUID> postsWithSnapshots,
                                                       String profileUsername) {
        return posts.stream().map(p -> new PostSummaryResponse(
                p.getId(), p.getTitle(), p.getFeedbackType(),
                p.getGraphId(), p.getCreatedAt(),
                bookmarkCounts.getOrDefault(p.getId(), 0L),
                myBookmarks.contains(p.getId()),
                p.getGraphId() != null || postsWithSnapshots.contains(p.getId()),
                com.codeprint.shared.GithubRepoOwner.matches(p.getRepoUrl(), profileUsername)
        )).toList();
    }

    // 공개 유저 프로필 응답 DTO
    public record UserProfileResponse(UUID id, String username, String avatarUrl, Instant createdAt) {}

    // 공개 프로젝트 응답 DTO
    public record PublicProjectResponse(UUID id, String name, String description, Instant createdAt) {}

    // 게시글 요약 응답 DTO
    public record PostSummaryResponse(
            UUID id, String title, String feedbackType, UUID graphId,
            Instant createdAt, long bookmarkCount, boolean bookmarkedByMe, boolean hasGraph, boolean ownRepo) {}
}
