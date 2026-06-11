// 게시글 좋아요 도메인 Repository 인터페이스
package com.codeprint.domain.community;

import java.util.UUID;

public interface PostLikeRepository {

    // 좋아요 존재 여부 확인
    boolean existsByUserIdAndPostId(UUID userId, UUID postId);

    // 좋아요 저장
    PostLike save(PostLike like);

    // 좋아요 삭제
    void deleteByUserIdAndPostId(UUID userId, UUID postId);

    // 게시글 좋아요 수 조회
    long countByPostId(UUID postId);
}
