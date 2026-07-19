// 게시글 첨부파일 도메인 엔티티
package com.codeprint.domain.community;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

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

    // S3Service.generatePresignedUploadUrl이 발급하는 키 형식과 동일해야 함(버킷 내 타 프리픽스 참조 차단)
    private static final Pattern VALID_S3_KEY = Pattern.compile(
            "^attachments/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}/.+$");

    // S3 업로드 완료 후 메타데이터를 저장하는 인스턴스 생성
    public static PostAttachment create(UUID postId, String s3Key, String originalFilename, String contentType) {
        if (s3Key == null || !VALID_S3_KEY.matcher(s3Key).matches()) {
            throw new IllegalArgumentException("유효하지 않은 첨부파일 키입니다: " + s3Key);
        }
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
