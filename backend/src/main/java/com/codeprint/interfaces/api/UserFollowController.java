// 유저 팔로우/팔로워 REST API
package com.codeprint.interfaces.api;

import com.codeprint.application.user.UserQueryService;
import com.codeprint.domain.user.User;
import com.codeprint.domain.user.UserFollow;
import com.codeprint.domain.user.UserFollowRepository;
import com.codeprint.shared.event.UserFollowedEvent;
import com.codeprint.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserFollowController {

    private final UserFollowRepository userFollowRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final UserQueryService userQueryService;

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

    // UserFollow 목록 → 유저 정보 목록 변환 — 유저를 findByIdIn으로 일괄 조회해 N+1 제거(팔로우 순서 보존)
    private List<Map<String, Object>> toUserList(List<UserFollow> follows, boolean useFollower) {
        if (follows.isEmpty()) return List.of();
        List<UUID> uids = follows.stream()
                .map(f -> useFollower ? f.getFollowerId() : f.getFollowingId())
                .toList();
        Map<UUID, User> users = new HashMap<>();
        for (User u : userRepository.findByIdIn(uids)) users.put(u.getId(), u);
        return uids.stream()
                .map(users::get)
                .filter(Objects::nonNull)
                .map(u -> Map.<String, Object>of(
                        "id", u.getId(),
                        "username", u.getUsername(),
                        "avatarUrl", u.getAvatarUrl() != null ? userQueryService.toPresignedAvatarUrl(u.getAvatarUrl()) : "https://github.com/" + u.getUsername() + ".png"
                ))
                .toList();
    }
}
