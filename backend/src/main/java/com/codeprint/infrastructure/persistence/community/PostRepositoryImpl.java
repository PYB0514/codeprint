package com.codeprint.infrastructure.persistence.community;

import com.codeprint.domain.community.Comment;
import com.codeprint.domain.community.Post;
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

    @Override
    public Post save(Post post) {
        return postJpa.save(post);
    }

    @Override
    public Optional<Post> findById(UUID id) {
        return postJpa.findById(id);
    }

    @Override
    public List<Post> findAll(int page, int size) {
        return postJpa.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));
    }

    @Override
    public List<Post> findByUserId(UUID userId) {
        return postJpa.findByUserId(userId);
    }

    @Override
    public Comment saveComment(Comment comment) {
        return commentJpa.save(comment);
    }

    @Override
    public List<Comment> findCommentsByPostId(UUID postId) {
        return commentJpa.findByPostIdOrderByCreatedAtAsc(postId);
    }

    @Override
    public void deleteById(UUID id) {
        postJpa.deleteById(id);
    }
}
