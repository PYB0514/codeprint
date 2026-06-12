// Community 도메인에서 팔로잉 관계를 조회하는 포트 인터페이스
package com.codeprint.domain.community.port;

import java.util.List;
import java.util.UUID;

public interface FollowQueryPort {

    // 특정 유저가 팔로우하는 유저 ID 목록 조회
    List<UUID> findFollowingIds(UUID followerId);
}
