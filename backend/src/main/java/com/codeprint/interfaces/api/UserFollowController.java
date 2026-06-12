// 유저 팔로우/팔로워 REST API
package com.codeprint.interfaces.api;

import com.codeprint.domain.user.User;
import com.codeprint.domain.user.UserFollow;
import com.codeprint.domain.user.UserFollowRepository;
import com.codeprint.domain.user.UserFollowedEvent;
import com.codeprint.domain.user.UserRepository;
import com.codeprint.infrastructure.storage.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserFollowController {

    private final UserFollowRepository userFollowRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final S3Service s3Service;

    // 팔로우
    @PostMapping("/{userId}/follow")
    public ResponseEntity<Map<String, Object>> follow(
            @PathVariable UUID userId,
            @AuthenticationPrincipal User me) {

        if (me.getId().equals(userId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "자기 자신은 팔로우할 수 없습니다."));
        }
        if (!userRepository.findById(userId).isPresent()) {
            return ResponseEntity.notFound().build();
        }
        if (userFollowRepository.existsByFollowerIdAndFollowingId(me.getId(), userId)) {
            return ResponseEntity.ok(Map.of("following", true));
        }
        userFollowRepository.save(UserFollow.create(me.getId(), userId));
        eventPublisher.publishEvent(new UserFollowedEvent(me.getId(), me.getUsername(), userId));
        return ResponseEntity.ok(Map.of("following", true));
    }

    // 언팔로우
    @DeleteMapping("/{userId}/follow")
    public ResponseEntity<Map<String, Object>> unfollow(
            @PathVariable UUID userId,
            @AuthenticationPrincipal User me) {

        userFollowRepository.deleteByFollowerIdAndFollowingId(me.getId(), userId);
        return ResponseEntity.ok(Map.of("following", false));
    }

    // 팔로우 상태 조회
    @GetMapping("/{userId}/follow")
    public ResponseEntity<Map<String, Object>> followStatus(
            @PathVariable UUID userId,
            @AuthenticationPrincipal User me) {

        boolean isFollowing = me != null && userFollowRepository.existsByFollowerIdAndFollowingId(me.getId(), userId);
        long followerCount = userFollowRepository.countByFollowingId(userId);
        long followingCount = userFollowRepository.countByFollowerId(userId);
        return ResponseEntity.ok(Map.of("following", isFollowing, "followers", followerCount, "followingCount", followingCount));
    }

    // 팔로워 목록
    @GetMapping("/{userId}/followers")
    public ResponseEntity<List<Map<String, Object>>> followers(@PathVariable UUID userId) {
        return ResponseEntity.ok(toUserList(userFollowRepository.findByFollowingId(userId), true));
    }

    // 팔로잉 목록
    @GetMapping("/{userId}/following")
    public ResponseEntity<List<Map<String, Object>>> following(@PathVariable UUID userId) {
        return ResponseEntity.ok(toUserList(userFollowRepository.findByFollowerId(userId), false));
    }

    // UserFollow 목록 → 유저 정보 목록 변환
    private List<Map<String, Object>> toUserList(List<UserFollow> follows, boolean useFollower) {
        return follows.stream()
                .map(f -> useFollower ? f.getFollowerId() : f.getFollowingId())
                .flatMap(uid -> userRepository.findById(uid).stream())
                .map(u -> Map.<String, Object>of(
                        "id", u.getId(),
                        "username", u.getUsername(),
                        "avatarUrl", u.getAvatarUrl() != null ? s3Service.toPresignedUrl(u.getAvatarUrl()) : "https://github.com/" + u.getUsername() + ".png"
                ))
                .toList();
    }
}
