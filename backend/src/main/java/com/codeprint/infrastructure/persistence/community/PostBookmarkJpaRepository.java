// 게시글 북마크 JPA Repository
package com.codeprint.infrastructure.persistence.community;

import com.codeprint.domain.community.PostBookmark;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PostBookmarkJpaRepository extends JpaRepository<PostBookmark, UUID>, com.codeprint.domain.community.PostBookmarkRepository {

    // 특정 유저의 특정 게시글 북마크 조회
    Optional<PostBookmark> findByUserIdAndPostId(UUID userId, UUID postId);

    // 특정 유저가 게시글을 북마크했는지 여부
    boolean existsByUserIdAndPostId(UUID userId, UUID postId);

    // 특정 유저의 북마크 목록 (최신순, Pageable 버전)
    Page<PostBookmark> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    // 도메인 인터페이스 구현 — limit 건 조회
    @Override
    default List<PostBookmark> findByUserIdOrderByCreatedAtDesc(UUID userId, int limit) {
        return findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, limit)).getContent();
    }

    // 게시글별 북마크 수
    long countByPostId(UUID postId);

    // 여러 게시글의 북마크 수 일괄 조회 (GROUP BY) — [postId, count] 행 목록
    @Override
    @Query("SELECT b.postId, COUNT(b) FROM PostBookmark b WHERE b.postId IN :postIds GROUP BY b.postId")
    List<Object[]> countByPostIdIn(@Param("postIds") List<UUID> postIds);

    // 특정 유저가 지정 게시글들 중 북마크한 항목 (파생 쿼리)
    @Override
    List<PostBookmark> findByUserIdAndPostIdIn(UUID userId, List<UUID> postIds);

    // 특정 유저+게시글 북마크 삭제
    void deleteByUserIdAndPostId(UUID userId, UUID postId);
}
