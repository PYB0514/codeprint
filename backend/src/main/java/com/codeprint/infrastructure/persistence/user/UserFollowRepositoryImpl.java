// 유저 팔로우 Repository JPA 구현체
package com.codeprint.infrastructure.persistence.user;

import com.codeprint.domain.user.UserFollow;
import com.codeprint.domain.user.UserFollowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class UserFollowRepositoryImpl implements UserFollowRepository {

    private final UserFollowJpaRepository jpa;

    // 팔로우 관계 저장
    public UserFollow save(UserFollow follow) {
        return jpa.save(follow);
    }

    // 팔로우 관계 존재 여부
    public boolean existsByFollowerIdAndFollowingId(UUID followerId, UUID followingId) {
        return jpa.existsByFollowerIdAndFollowingId(followerId, followingId);
    }

    // 팔로우 관계 삭제
    @Transactional
    public void deleteByFollowerIdAndFollowingId(UUID followerId, UUID followingId) {
        jpa.deleteByFollowerIdAndFollowingId(followerId, followingId);
    }

    // 팔로워 목록 조회
    public List<UserFollow> findByFollowingId(UUID followingId) {
        return jpa.findByFollowingId(followingId);
    }

    // 팔로잉 목록 조회
    public List<UserFollow> findByFollowerId(UUID followerId) {
        return jpa.findByFollowerId(followerId);
    }

    // 팔로워 수
    public long countByFollowingId(UUID followingId) {
        return jpa.countByFollowingId(followingId);
    }

    // 팔로잉 수
    public long countByFollowerId(UUID followerId) {
        return jpa.countByFollowerId(followerId);
    }
}
