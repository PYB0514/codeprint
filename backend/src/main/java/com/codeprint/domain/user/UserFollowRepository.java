// 유저 팔로우 관계 리포지토리 인터페이스
package com.codeprint.domain.user;

import java.util.List;
import java.util.UUID;

public interface UserFollowRepository {

    // 팔로우 저장
    UserFollow save(UserFollow follow);

    // 팔로우 관계 존재 여부 확인
    boolean existsByFollowerIdAndFollowingId(UUID followerId, UUID followingId);

    // 팔로우 관계 삭제
    void deleteByFollowerIdAndFollowingId(UUID followerId, UUID followingId);

    // 특정 유저를 팔로우하는 사람 목록 (팔로워 목록)
    List<UserFollow> findByFollowingId(UUID followingId);

    // 특정 유저가 팔로우하는 사람 목록 (팔로잉 목록)
    List<UserFollow> findByFollowerId(UUID followerId);

    // 팔로워 수
    long countByFollowingId(UUID followingId);

    // 팔로잉 수
    long countByFollowerId(UUID followerId);
}
