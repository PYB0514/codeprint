// 팔로우 도메인 이벤트 — 알림 발송 side effect 분리용
package com.codeprint.domain.user;

import java.util.UUID;

public record UserFollowedEvent(UUID followerId, String followerUsername, UUID followingId) {}
