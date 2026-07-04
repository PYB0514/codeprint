// 커뮤니티 게시글 도메인 Repository JPA 구현체
package com.codeprint.infrastructure.persistence.community;

import com.codeprint.domain.community.Comment;
import com.codeprint.domain.community.Post;
import com.codeprint.domain.community.PostAttachment;
import com.codeprint.domain.community.PostGraphSnapshot;
import com.codeprint.domain.community.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PostRepositoryImpl implements PostRepository {

    private final PostJpaRepository postJpa;
    private final CommentJpaRepository commentJpa;
    private final PostAttachmentJpaRepository attachmentJpa;
    private final PostGraphSnapshotJpaRepository snapshotJpa;

    // 게시글 엔티티를 저장하고 반환
    @Override
    public Post save(Post post) {
        return postJpa.save(post);
    }

    // UUID로 게시글 조회
    @Override
    public Optional<Post> findById(UUID id) {
        return postJpa.findById(id);
    }

    // 최신순 페이지 단위로 게시글 목록 조회
    @Override
    public List<Post> findAll(int page, int size) {
        return postJpa.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));
    }

    // 사용자 ID로 게시글 목록 조회
    @Override
    public List<Post> findByUserId(UUID userId) {
        return postJpa.findByUserId(userId);
    }

    // 댓글 엔티티를 저장하고 반환
    @Override
    public Comment saveComment(Comment comment) {
        return commentJpa.save(comment);
    }

    // 게시글 ID로 댓글 목록을 작성 순으로 조회
    @Override
    public List<Comment> findCommentsByPostId(UUID postId) {
        return commentJpa.findByPostIdOrderByCreatedAtAsc(postId);
    }

    // 댓글 단건 조회
    @Override
    public Optional<Comment> findCommentById(UUID commentId) {
        return commentJpa.findById(commentId);
    }

    // 댓글 삭제
    @Override
    public void deleteCommentById(UUID commentId) {
        commentJpa.deleteById(commentId);
    }

    // UUID로 게시글 삭제
    @Override
    public void deleteById(UUID id) {
        postJpa.deleteById(id);
    }

    // 첨부파일 메타데이터 저장
    @Override
    public PostAttachment saveAttachment(PostAttachment attachment) {
        return attachmentJpa.save(attachment);
    }

    // 게시글 ID로 첨부파일 목록 조회
    @Override
    public List<PostAttachment> findAttachmentsByPostId(UUID postId) {
        return attachmentJpa.findByPostIdOrderByCreatedAtAsc(postId);
    }

    // 제목/본문 키워드 검색
    @Override
    public List<Post> findByTitleContainingIgnoreCaseOrContentContainingIgnoreCaseOrderByCreatedAtDesc(
            String title, String content, Pageable pageable) {
        return postJpa.findByTitleContainingIgnoreCaseOrContentContainingIgnoreCaseOrderByCreatedAtDesc(title, content, pageable);
    }

    // 최신순 페이지 목록 (Pageable 버전)
    @Override
    public List<Post> findAllByOrderByCreatedAtDesc(Pageable pageable) {
        return postJpa.findAllByOrderByCreatedAtDesc(pageable);
    }

    // 팔로잉 유저 게시글 최신순 조회
    @Override
    public List<Post> findByUserIdInOrderByCreatedAtDesc(List<UUID> userIds, Pageable pageable) {
        return postJpa.findByUserIdInOrderByCreatedAtDesc(userIds, pageable);
    }

    // 게시글 댓글 수 조회
    @Override
    public long countCommentsByPostId(UUID postId) {
        return commentJpa.countByPostId(postId);
    }

    // 여러 게시글의 댓글 수 일괄 조회 (N+1 제거)
    @Override
    public List<Object[]> countCommentsByPostIdIn(List<UUID> postIds) {
        return commentJpa.countByPostIdIn(postIds);
    }

    // 좋아요 수 내림차순 게시글 목록 조회
    @Override
    public List<Post> findAllOrderByLikeCountDesc(Pageable pageable) {
        return postJpa.findAllOrderByLikeCountDesc(pageable);
    }

    // 조회수 내림차순 게시글 목록 조회
    @Override
    public List<Post> findAllByOrderByViewCountDesc(Pageable pageable) {
        return postJpa.findAllByOrderByViewCountDesc(pageable);
    }

    // 그래프가 첨부된 게시글만 최신순 조회 (레거시 단일 첨부 + 신규 다중 스냅샷 모두 포함)
    @Override
    public List<Post> findWithGraphOrSnapshots(Pageable pageable) {
        return postJpa.findWithGraphOrSnapshotsOrderByCreatedAtDesc(pageable);
    }

    // 그래프 스냅샷 목록 저장
    @Override
    public void saveSnapshots(List<PostGraphSnapshot> snapshots) {
        snapshotJpa.saveAll(snapshots);
    }

    // 게시글 ID로 그래프 스냅샷 목록 조회 (노출 순)
    @Override
    public List<PostGraphSnapshot> findSnapshotsByPostId(UUID postId) {
        return snapshotJpa.findByPostIdOrderByPositionAsc(postId);
    }

    // 주어진 게시글 ID 중 그래프 스냅샷을 가진 것만 반환
    @Override
    public List<UUID> findPostIdsWithSnapshots(List<UUID> postIds) {
        return snapshotJpa.findDistinctPostIdsByPostIdIn(postIds);
    }
}
