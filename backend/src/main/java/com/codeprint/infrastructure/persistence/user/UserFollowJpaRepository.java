// 유저 팔로우 JPA 리포지토리
package com.codeprint.infrastructure.persistence.user;

import com.codeprint.domain.user.UserFollow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserFollowJpaRepository extends JpaRepository<UserFollow, UUID> {

    boolean existsByFollowerIdAndFollowingId(UUID followerId, UUID followingId);

    void deleteByFollowerIdAndFollowingId(UUID followerId, UUID followingId);

    List<UserFollow> findByFollowingId(UUID followingId);

    List<UserFollow> findByFollowerId(UUID followerId);

    long countByFollowingId(UUID followingId);

    long countByFollowerId(UUID followerId);
}
