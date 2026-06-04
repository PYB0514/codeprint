// S3 presigned URL 발급 및 파일 삭제 서비스
package com.codeprint.infrastructure.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class S3Service {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${aws.s3.bucket}")
    private String bucket;

    // 업로드용 presigned URL 발급 (유효 시간 5분)
    public PresignedUploadResult generatePresignedUploadUrl(String contentType, String originalFilename) {
        String key = "attachments/" + UUID.randomUUID() + "/" + originalFilename;

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build();

        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(r ->
                r.signatureDuration(Duration.ofMinutes(5))
                 .putObjectRequest(putRequest));

        return new PresignedUploadResult(presigned.url().toString(), key);
    }

    // 조회용 presigned GET URL 발급 (유효 시간 1시간)
    public String generatePresignedDownloadUrl(String key) {
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(r ->
                r.signatureDuration(Duration.ofHours(1))
                 .getObjectRequest(getRequest));

        return presigned.url().toString();
    }

    // S3 객체 삭제
    public void deleteObject(String key) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build());
    }

    // presigned URL과 S3 키를 담는 결과 레코드
    public record PresignedUploadResult(String uploadUrl, String s3Key) {}
}
