// 커뮤니티 게시글 도메인 Repository 인터페이스
package com.codeprint.domain.community;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PostRepository {

    Post save(Post post);

    Optional<Post> findById(UUID id);

    List<Post> findAll(int page, int size);

    List<Post> findByUserId(UUID userId);

    Comment saveComment(Comment comment);

    List<Comment> findCommentsByPostId(UUID postId);

    void deleteById(UUID id);

    PostAttachment saveAttachment(PostAttachment attachment);

    List<PostAttachment> findAttachmentsByPostId(UUID postId);

    // 제목/본문 검색 (대소문자 무시)
    List<Post> findByTitleContainingIgnoreCaseOrContentContainingIgnoreCaseOrderByCreatedAtDesc(
            String title, String content, org.springframework.data.domain.Pageable pageable);

    // 최신순 페이지 목록
    List<Post> findAllByOrderByCreatedAtDesc(org.springframework.data.domain.Pageable pageable);
}
