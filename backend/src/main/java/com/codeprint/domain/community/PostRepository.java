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
}
