// 게시글 좋아요 도메인 Repository 인터페이스
package com.codeprint.domain.community;

import java.util.List;
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

    // 여러 게시글의 좋아요 수 일괄 조회 — 결과는 [postId(UUID), count(Long)] 행 목록 (N+1 제거용)
    List<Object[]> countByPostIdIn(List<UUID> postIds);

    // 특정 유저가 지정 게시글들 중 좋아요한 항목 (목록 화면의 "내 좋아요 여부" 일괄 판정용)
    List<PostLike> findByUserIdAndPostIdIn(UUID userId, List<UUID> postIds);
}
