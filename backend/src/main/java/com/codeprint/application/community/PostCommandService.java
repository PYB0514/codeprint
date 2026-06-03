// 게시글/댓글 CRUD 애플리케이션 서비스
package com.codeprint.application.community;

import com.codeprint.domain.community.Comment;
import com.codeprint.domain.community.Post;
import com.codeprint.domain.community.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class PostCommandService {

    private final PostRepository postRepository;

    // 새 게시글을 생성하여 저장
    public Post createPost(UUID userId, UUID graphId, String title, String content, String feedbackType,
                           List<String> hiddenLayers, List<String> hiddenGroups, List<String> hiddenNodeNames) {
        Post post = Post.create(userId, graphId, title, content, feedbackType, hiddenLayers, hiddenGroups, hiddenNodeNames);
        return postRepository.save(post);
    }

    // 게시글 ID로 단건 조회
    @Transactional(readOnly = true)
    public Optional<Post> findById(UUID postId) {
        return postRepository.findById(postId);
    }

    // 게시글에 댓글을 추가
    public Comment addComment(UUID postId, UUID userId, String content) {
        postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found: " + postId));
        Comment comment = Comment.create(postId, userId, content);
        return postRepository.saveComment(comment);
    }

    // 소유자 확인 후 게시글 삭제
    public void deletePost(UUID postId, UUID requestingUserId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found: " + postId));
        if (!post.getUserId().equals(requestingUserId)) {
            throw new IllegalStateException("Not authorized to delete this post");
        }
        postRepository.deleteById(postId);
    }

    // 최신순으로 게시글 목록을 페이지 조회
    @Transactional(readOnly = true)
    public List<Post> getPosts(int page, int size) {
        return postRepository.findAll(page, size);
    }

    // 게시글 ID로 댓글 목록 조회
    @Transactional(readOnly = true)
    public List<Comment> getComments(UUID postId) {
        return postRepository.findCommentsByPostId(postId);
    }
}
