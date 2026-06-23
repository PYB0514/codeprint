// 커뮤니티 게시글 도메인 Repository 인터페이스
package com.codeprint.domain.community;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PostRepository {

    // 게시글 저장
    Post save(Post post);

    // ID로 게시글 조회
    Optional<Post> findById(UUID id);

    // 전체 게시글 페이지 조회
    List<Post> findAll(int page, int size);

    // 사용자 게시글 목록 조회
    List<Post> findByUserId(UUID userId);

    // 댓글 저장
    Comment saveComment(Comment comment);

    // 게시글 댓글 목록 조회
    List<Comment> findCommentsByPostId(UUID postId);

    // 댓글 단건 조회
    Optional<Comment> findCommentById(UUID commentId);

    // 댓글 삭제
    void deleteCommentById(UUID commentId);

    // 게시글 삭제
    void deleteById(UUID id);

    // 첨부파일 저장
    PostAttachment saveAttachment(PostAttachment attachment);

    // 게시글 첨부파일 목록 조회
    List<PostAttachment> findAttachmentsByPostId(UUID postId);

    // 제목/본문 검색 (대소문자 무시)
    List<Post> findByTitleContainingIgnoreCaseOrContentContainingIgnoreCaseOrderByCreatedAtDesc(
            String title, String content, org.springframework.data.domain.Pageable pageable);

    // 최신순 페이지 목록
    List<Post> findAllByOrderByCreatedAtDesc(org.springframework.data.domain.Pageable pageable);

    // 특정 유저 목록의 게시글 최신순 조회 (팔로잉 피드)
    List<Post> findByUserIdInOrderByCreatedAtDesc(List<UUID> userIds, org.springframework.data.domain.Pageable pageable);

    // 게시글 댓글 수 조회
    long countCommentsByPostId(UUID postId);

    // 여러 게시글의 댓글 수 일괄 조회 — 결과는 [postId(UUID), count(Long)] 행 목록 (N+1 제거용)
    List<Object[]> countCommentsByPostIdIn(List<UUID> postIds);

    // 좋아요 수 내림차순 게시글 목록 조회
    List<Post> findAllOrderByLikeCountDesc(org.springframework.data.domain.Pageable pageable);

    // 조회수 내림차순 게시글 목록 조회
    List<Post> findAllByOrderByViewCountDesc(org.springframework.data.domain.Pageable pageable);

    // 그래프 첨부 게시글만 최신순 조회
    List<Post> findByGraphIdNotNull(org.springframework.data.domain.Pageable pageable);
}
