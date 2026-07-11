// 첨부파일 업로드용 S3 presigned URL 발급 응용 서비스
package com.codeprint.application.attachment;

import com.codeprint.infrastructure.storage.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AttachmentPresignService {

    private final S3Service s3Service;

    // presigned 업로드 URL·S3 키 발급
    public PresignedUpload generateUploadUrl(String contentType, String filename) {
        S3Service.PresignedUploadResult result = s3Service.generatePresignedUploadUrl(contentType, filename);
        return new PresignedUpload(result.uploadUrl(), result.s3Key());
    }

    public record PresignedUpload(String uploadUrl, String s3Key) {}
}
