// 게시글 JPA Repository (Spring Data)
package com.codeprint.infrastructure.persistence.community;

import com.codeprint.domain.community.Post;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface PostJpaRepository extends JpaRepository<Post, UUID> {

    // 유저 ID로 게시글 조회
    List<Post> findByUserId(UUID userId);

    // 최신순 게시글 페이지 조회
    List<Post> findAllByOrderByCreatedAtDesc(Pageable pageable);

    // 제목 또는 본문에 키워드가 포함된 게시글 검색 (대소문자 무시)
    List<Post> findByTitleContainingIgnoreCaseOrContentContainingIgnoreCaseOrderByCreatedAtDesc(
            String title, String content, Pageable pageable);

    // 특정 유저 목록의 게시글 최신순 조회
    List<Post> findByUserIdInOrderByCreatedAtDesc(List<UUID> userIds, Pageable pageable);

    // 좋아요 수 내림차순 조회 (post_likes COUNT 기준)
    @Query("SELECT p FROM Post p ORDER BY (SELECT COUNT(l) FROM PostLike l WHERE l.postId = p.id) DESC, p.createdAt DESC")
    List<Post> findAllOrderByLikeCountDesc(Pageable pageable);

    // 조회수 내림차순 조회
    List<Post> findAllByOrderByViewCountDesc(Pageable pageable);

    // 그래프가 첨부된 게시글만 최신순 조회 — 레거시 단일 첨부(graphId) 또는 신규 다중 스냅샷 둘 다 포함
    @Query("SELECT p FROM Post p WHERE p.graphId IS NOT NULL OR EXISTS (SELECT 1 FROM PostGraphSnapshot s WHERE s.postId = p.id) ORDER BY p.createdAt DESC")
    List<Post> findWithGraphOrSnapshotsOrderByCreatedAtDesc(Pageable pageable);
}
