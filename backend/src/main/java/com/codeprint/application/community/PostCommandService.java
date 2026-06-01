package com.codeprint.application.community;

import com.codeprint.domain.community.Comment;
import com.codeprint.domain.community.Post;
import com.codeprint.domain.community.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class PostCommandService {

    private final PostRepository postRepository;

    public Post createPost(UUID userId, UUID graphId, String title, String content, String feedbackType) {
        Post post = Post.create(userId, graphId, title, content, feedbackType);
        return postRepository.save(post);
    }

    public Comment addComment(UUID postId, UUID userId, String content) {
        postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found: " + postId));
        Comment comment = Comment.create(postId, userId, content);
        return postRepository.saveComment(comment);
    }

    public void deletePost(UUID postId, UUID requestingUserId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found: " + postId));
        if (!post.getUserId().equals(requestingUserId)) {
            throw new IllegalStateException("Not authorized to delete this post");
        }
        postRepository.deleteById(postId);
    }

    @Transactional(readOnly = true)
    public List<Post> getPosts(int page, int size) {
        return postRepository.findAll(page, size);
    }

    @Transactional(readOnly = true)
    public List<Comment> getComments(UUID postId) {
        return postRepository.findCommentsByPostId(postId);
    }
}
