// 게시글 첨부파일 도메인 엔티티
package com.codeprint.domain.community;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "post_attachments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostAttachment {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "post_id", nullable = false, columnDefinition = "uuid")
    private UUID postId;

    @Column(name = "s3_key", nullable = false, length = 500)
    private String s3Key;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // S3 업로드 완료 후 메타데이터를 저장하는 인스턴스 생성
    public static PostAttachment create(UUID postId, String s3Key, String originalFilename, String contentType) {
        PostAttachment a = new PostAttachment();
        a.id = UUID.randomUUID();
        a.postId = postId;
        a.s3Key = s3Key;
        a.originalFilename = originalFilename;
        a.contentType = contentType;
        a.createdAt = Instant.now();
        return a;
    }
}
