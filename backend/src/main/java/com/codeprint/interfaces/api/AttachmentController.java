// S3 presigned URL 발급 API
package com.codeprint.interfaces.api;

import com.codeprint.infrastructure.security.JwtTokenProvider;
import com.codeprint.infrastructure.storage.S3Service;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/attachments")
@RequiredArgsConstructor
public class AttachmentController {

    private final S3Service s3Service;
    private final JwtTokenProvider jwtTokenProvider;

    // presigned URL 발급 — 클라이언트가 S3에 직접 업로드하기 위한 URL 반환
    @PostMapping("/presign")
    public ResponseEntity<Map<String, String>> presign(
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {

        String contentType = body.get("contentType");
        String filename = body.get("filename");

        if (contentType == null || filename == null) {
            return ResponseEntity.badRequest().build();
        }

        S3Service.PresignedUploadResult result = s3Service.generatePresignedUploadUrl(contentType, filename);

        return ResponseEntity.ok(Map.of(
                "uploadUrl", result.uploadUrl(),
                "s3Key", result.s3Key()
        ));
    }
}
