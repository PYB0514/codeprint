// S3 presigned URL 발급 API
package com.codeprint.interfaces.api;

import com.codeprint.domain.user.User;
import com.codeprint.infrastructure.storage.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/attachments")
@RequiredArgsConstructor
public class AttachmentController {

    private final S3Service s3Service;

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp"
    );
    private static final int MAX_FILENAME_LENGTH = 255;

    // presigned URL 발급 — 인증된 사용자만 허용, 이미지 타입만 허용
    @PostMapping("/presign")
    public ResponseEntity<Map<String, String>> presign(
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal User user) {

        String contentType = body.get("contentType");
        String filename = body.get("filename");

        if (contentType == null || filename == null) {
            return ResponseEntity.badRequest().build();
        }
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            return ResponseEntity.badRequest().build();
        }
        if (filename.length() > MAX_FILENAME_LENGTH || filename.contains("..")) {
            return ResponseEntity.badRequest().build();
        }

        S3Service.PresignedUploadResult result = s3Service.generatePresignedUploadUrl(contentType, filename);

        return ResponseEntity.ok(Map.of(
                "uploadUrl", result.uploadUrl(),
                "s3Key", result.s3Key()
        ));
    }
}
