// 게시글 좋아요 JPA Repository
package com.codeprint.infrastructure.persistence.community;

import com.codeprint.domain.community.PostLike;
import com.codeprint.domain.community.PostLikeRepository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PostLikeJpaRepository extends JpaRepository<PostLike, UUID>, PostLikeRepository {

    // 특정 유저+게시글 좋아요 존재 여부
    boolean existsByUserIdAndPostId(UUID userId, UUID postId);

    // 게시글별 좋아요 수
    long countByPostId(UUID postId);

    // 특정 유저+게시글 좋아요 삭제
    void deleteByUserIdAndPostId(UUID userId, UUID postId);
}
