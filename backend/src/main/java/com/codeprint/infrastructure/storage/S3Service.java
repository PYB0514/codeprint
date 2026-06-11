// S3 presigned URL 발급 및 파일 삭제 서비스
package com.codeprint.infrastructure.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import java.io.IOException;
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

    // 조회용 presigned GET URL 발급 (유효 시간 15분)
    public String generatePresignedDownloadUrl(String key) {
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(r ->
                r.signatureDuration(Duration.ofMinutes(15))
                 .getObjectRequest(getRequest));

        return presigned.url().toString();
    }

    // multipart 파일을 지정 경로에 업로드하고 공개 URL 반환
    public String uploadFile(MultipartFile file, String keyPrefix) throws IOException {
        String originalFilename = file.getOriginalFilename();
        String ext = (originalFilename != null && originalFilename.contains("."))
                ? originalFilename.substring(originalFilename.lastIndexOf('.'))
                : "";
        String key = keyPrefix + UUID.randomUUID() + ext;

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(file.getContentType())
                .build();

        s3Client.putObject(putRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

        return "https://" + bucket + ".s3.amazonaws.com/" + key;
    }

    // 저장된 S3 URL(또는 키)을 7일 만료 presigned GET URL로 변환
    public String toPresignedUrl(String storedUrl) {
        if (storedUrl == null || storedUrl.isBlank()) return null;
        String key = storedUrl.contains(".amazonaws.com/")
                ? storedUrl.substring(storedUrl.indexOf(".amazonaws.com/") + ".amazonaws.com/".length())
                : storedUrl;
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();
        PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(r ->
                r.signatureDuration(Duration.ofDays(7))
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
