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

    // 여러 게시글의 북마크 수 일괄 조회 — 결과는 [postId(UUID), count(Long)] 행 목록 (N+1 제거용)
    List<Object[]> countByPostIdIn(List<UUID> postIds);

    // 특정 유저가 지정 게시글들 중 북마크한 항목 (목록 화면의 "내 북마크 여부" 일괄 판정용)
    List<PostBookmark> findByUserIdAndPostIdIn(UUID userId, List<UUID> postIds);

    // 특정 유저의 북마크 목록 (최신순, 최대 limit 건)
    List<PostBookmark> findByUserIdOrderByCreatedAtDesc(UUID userId, int limit);
}
