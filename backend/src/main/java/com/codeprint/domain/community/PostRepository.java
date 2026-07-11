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

    // 그래프가 첨부된 게시글만 최신순 조회 (레거시 단일 첨부 + 신규 다중 스냅샷 모두 포함)
    List<Post> findWithGraphOrSnapshots(org.springframework.data.domain.Pageable pageable);

    // 그래프 스냅샷 목록 저장
    void saveSnapshots(List<PostGraphSnapshot> snapshots);

    // 게시글의 기존 그래프 스냅샷을 전부 삭제 — 스냅샷 교체 저장 전 호출용
    void deleteSnapshotsByPostId(UUID postId);

    // 게시글 ID로 그래프 스냅샷 목록 조회 (노출 순)
    List<PostGraphSnapshot> findSnapshotsByPostId(UUID postId);

    // 주어진 게시글 ID 중 그래프 스냅샷을 가진 것만 반환 (N+1 제거용 배치 존재 확인)
    List<UUID> findPostIdsWithSnapshots(List<UUID> postIds);
}
