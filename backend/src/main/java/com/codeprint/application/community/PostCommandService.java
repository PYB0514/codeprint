// 게시글/댓글 CRUD 애플리케이션 서비스
package com.codeprint.application.community;

import com.codeprint.domain.community.Comment;
import com.codeprint.domain.community.Post;
import com.codeprint.domain.community.PostAttachment;
import com.codeprint.domain.community.PostGraphSnapshot;
import com.codeprint.domain.community.PostRepository;
import com.codeprint.infrastructure.storage.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class PostCommandService {

    private final PostRepository postRepository;
    private final S3Service s3Service;

    // 새 게시글을 생성하여 저장
    public Post createPost(UUID userId, UUID graphId, String title, String content, String feedbackType,
                           List<String> hiddenLayers, List<String> hiddenGroups, List<String> hiddenNodeNames,
                           String repoUrl) {
        Post post = Post.create(userId, graphId, title, content, feedbackType, hiddenLayers, hiddenGroups, hiddenNodeNames, repoUrl);
        return postRepository.save(post);
    }

    // 게시글 ID로 단건 조회
    @Transactional(readOnly = true)
    public Optional<Post> findById(UUID postId) {
        return postRepository.findById(postId);
    }

    // 게시글을 비공개로 전환 — 커뮤니티 피드 목록엔 안 뜨지만 직접 링크로는 계속 접근 가능
    public Post makePrivate(UUID postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found: " + postId));
        post.makePrivate();
        return postRepository.save(post);
    }

    // 프로젝트+프리셋 슬롯에서 캡처한 그래프 스냅샷 목록을 게시글에 첨부 저장
    public void saveGraphSnapshots(UUID postId, List<SnapshotToSave> snapshots) {
        List<PostGraphSnapshot> entities = new ArrayList<>();
        int position = 0;
        for (SnapshotToSave s : snapshots) {
            entities.add(PostGraphSnapshot.create(postId, s.projectId(), s.graphId(), s.config(), position++));
        }
        postRepository.saveSnapshots(entities);
    }

    // 게시글의 기존 스냅샷을 지우고 새 스냅샷으로 교체 — 매일 갱신되는 고정 게시글(오늘의 공개레포)용
    public void replaceGraphSnapshots(UUID postId, List<SnapshotToSave> snapshots) {
        postRepository.deleteSnapshotsByPostId(postId);
        saveGraphSnapshots(postId, snapshots);
    }

    // 게시글 ID로 그래프 스냅샷 목록 조회
    @Transactional(readOnly = true)
    public List<PostGraphSnapshot> getGraphSnapshots(UUID postId) {
        return postRepository.findSnapshotsByPostId(postId);
    }

    // 저장할 그래프 스냅샷 정보 — 캡처 시점의 graphId(불변)·config 사본
    public record SnapshotToSave(UUID projectId, UUID graphId, Map<String, Object> config) {}

    // 게시글에 댓글을 추가
    public Comment addComment(UUID postId, UUID userId, String content) {
        postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found: " + postId));
        Comment comment = Comment.create(postId, userId, content);
        return postRepository.saveComment(comment);
    }

    // 소유자 확인 후 댓글 삭제
    public void deleteComment(UUID commentId, UUID requestingUserId) {
        Comment comment = postRepository.findCommentById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("Comment not found: " + commentId));
        if (!comment.getUserId().equals(requestingUserId)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "댓글 작성자만 삭제할 수 있습니다.");
        }
        postRepository.deleteCommentById(commentId);
    }

    // 소유자 확인 후 게시글 제목/내용 수정
    public Post updatePost(UUID postId, UUID requestingUserId, String title, String content) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found: " + postId));
        if (!post.getUserId().equals(requestingUserId)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "게시글 작성자만 수정할 수 있습니다.");
        }
        post.update(title, content);
        return postRepository.save(post);
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

    // S3 키 목록을 받아 게시글 첨부파일로 저장
    public void saveAttachments(UUID postId, List<AttachmentInfo> attachments) {
        for (AttachmentInfo info : attachments) {
            PostAttachment attachment = PostAttachment.create(
                    postId, info.s3Key(), info.originalFilename(), info.contentType());
            postRepository.saveAttachment(attachment);
        }
    }

    // 게시글 첨부파일 목록과 presigned GET URL을 함께 반환
    @Transactional(readOnly = true)
    public List<AttachmentView> getAttachmentsWithUrls(UUID postId) {
        return postRepository.findAttachmentsByPostId(postId).stream()
                .map(a -> new AttachmentView(a.getId(), a.getS3Key(),
                        a.getOriginalFilename(), a.getContentType(),
                        s3Service.generatePresignedDownloadUrl(a.getS3Key())))
                .toList();
    }

    // 첨부파일 저장에 필요한 정보
    public record AttachmentInfo(String s3Key, String originalFilename, String contentType) {}

    // 첨부파일 조회 뷰 (presigned URL 포함)
    public record AttachmentView(java.util.UUID id, String s3Key, String originalFilename,
                                 String contentType, String url) {}

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
