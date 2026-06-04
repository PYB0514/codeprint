// 커뮤니티 게시글 도메인 Repository JPA 구현체
package com.codeprint.infrastructure.persistence.community;

import com.codeprint.domain.community.Comment;
import com.codeprint.domain.community.Post;
import com.codeprint.domain.community.PostAttachment;
import com.codeprint.domain.community.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
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
}
