// 공개 유저 프로필 조회 REST API
package com.codeprint.interfaces.api;

import com.codeprint.domain.community.Post;
import com.codeprint.domain.user.User;
import com.codeprint.domain.user.UserRepository;
import com.codeprint.infrastructure.persistence.community.PostBookmarkJpaRepository;
import com.codeprint.infrastructure.persistence.community.PostJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final PostJpaRepository postJpaRepository;
    private final PostBookmarkJpaRepository bookmarkRepository;

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
        List<PostSummaryResponse> posts = postJpaRepository.findByUserId(userId).stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(p -> toPostSummary(p, currentUser))
                .toList();
        return ResponseEntity.ok(posts);
    }

    // User → UserProfileResponse 변환
    private UserProfileResponse toProfile(User u) {
        String avatarUrl = "https://github.com/" + u.getUsername() + ".png";
        return new UserProfileResponse(u.getId(), u.getUsername(), avatarUrl, u.getCreatedAt());
    }

    // Post → PostSummaryResponse 변환
    private PostSummaryResponse toPostSummary(Post p, User currentUser) {
        long bookmarkCount = bookmarkRepository.countByPostId(p.getId());
        boolean bookmarkedByMe = currentUser != null &&
                bookmarkRepository.existsByUserIdAndPostId(currentUser.getId(), p.getId());
        return new PostSummaryResponse(
                p.getId(), p.getTitle(), p.getFeedbackType(),
                p.getGraphId(), p.getCreatedAt(), bookmarkCount, bookmarkedByMe);
    }

    // 공개 유저 프로필 응답 DTO
    public record UserProfileResponse(UUID id, String username, String avatarUrl, Instant createdAt) {}

    // 게시글 요약 응답 DTO
    public record PostSummaryResponse(
            UUID id, String title, String feedbackType, UUID graphId,
            Instant createdAt, long bookmarkCount, boolean bookmarkedByMe) {}
}
