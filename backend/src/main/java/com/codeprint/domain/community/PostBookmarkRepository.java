// 게시글 북마크 도메인 Repository 인터페이스
package com.codeprint.domain.community;

import java.util.List;
import java.util.UUID;

public interface PostBookmarkRepository {

    boolean existsByUserIdAndPostId(UUID userId, UUID postId);

    PostBookmark save(PostBookmark bookmark);

    void deleteByUserIdAndPostId(UUID userId, UUID postId);

    long countByPostId(UUID postId);

    // 특정 유저의 북마크 목록 (최신순, 최대 limit 건)
    List<PostBookmark> findByUserIdOrderByCreatedAtDesc(UUID userId, int limit);
}
