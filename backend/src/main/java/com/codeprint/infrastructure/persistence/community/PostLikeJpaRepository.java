// 게시글 좋아요 JPA Repository
package com.codeprint.infrastructure.persistence.community;

import com.codeprint.domain.community.PostLike;
import com.codeprint.domain.community.PostLikeRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface PostLikeJpaRepository extends JpaRepository<PostLike, UUID>, PostLikeRepository {

    // 특정 유저+게시글 좋아요 존재 여부
    boolean existsByUserIdAndPostId(UUID userId, UUID postId);

    // 게시글별 좋아요 수
    long countByPostId(UUID postId);

    // 여러 게시글의 좋아요 수 일괄 조회 (GROUP BY) — [postId, count] 행 목록
    @Override
    @Query("SELECT l.postId, COUNT(l) FROM PostLike l WHERE l.postId IN :postIds GROUP BY l.postId")
    List<Object[]> countByPostIdIn(@Param("postIds") List<UUID> postIds);

    // 특정 유저가 지정 게시글들 중 좋아요한 항목 (파생 쿼리)
    @Override
    List<PostLike> findByUserIdAndPostIdIn(UUID userId, List<UUID> postIds);

    // 특정 유저+게시글 좋아요 삭제
    @Transactional
    void deleteByUserIdAndPostId(UUID userId, UUID postId);
}
