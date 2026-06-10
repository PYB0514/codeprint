// 게시글 북마크 도메인 Repository 인터페이스
package com.codeprint.domain.community;

import java.util.List;
import java.util.UUID;

public interface PostBookmarkRepository {

    // 북마크 존재 여부 확인
    boolean existsByUserIdAndPostId(UUID userId, UUID postId);

    // 북마크 저장
    PostBookmark save(PostBookmark bookmark);

    // 북마크 삭제
    void deleteByUserIdAndPostId(UUID userId, UUID postId);

    // 게시글 북마크 수 조회
    long countByPostId(UUID postId);

    // 특정 유저의 북마크 목록 (최신순, 최대 limit 건)
    List<PostBookmark> findByUserIdOrderByCreatedAtDesc(UUID userId, int limit);
}
