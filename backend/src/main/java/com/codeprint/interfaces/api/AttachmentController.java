// S3 presigned URL 발급 API
package com.codeprint.interfaces.api;

import com.codeprint.application.attachment.AttachmentPresignService;
import com.codeprint.domain.user.User;
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

    private final AttachmentPresignService attachmentPresignService;

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp"
    );
    private static final int MAX_FILENAME_LENGTH = 255;
    // S3 presigned PUT URL에 Content-Length 제약이 없으므로 컨트롤러에서 크기 선언 검증
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    // presigned URL 발급 — 인증된 사용자만 허용, 이미지 타입/크기 검증
    @PostMapping("/presign")
    public ResponseEntity<Map<String, String>> presign(
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal User user) {

        String contentType = body.get("contentType");
        String filename = body.get("filename");
        String fileSizeStr = body.get("fileSize");

        if (contentType == null || filename == null) {
            return ResponseEntity.badRequest().build();
        }
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            return ResponseEntity.badRequest().build();
        }
        if (filename.length() > MAX_FILENAME_LENGTH || filename.contains("..")) {
            return ResponseEntity.badRequest().build();
        }
        if (fileSizeStr != null) {
            try {
                long fileSize = Long.parseLong(fileSizeStr);
                if (fileSize > MAX_FILE_SIZE) {
                    return ResponseEntity.badRequest().body(Map.of("error", "파일 크기는 10MB를 초과할 수 없습니다."));
                }
            } catch (NumberFormatException ignored) {
                return ResponseEntity.badRequest().build();
            }
        }

        var result = attachmentPresignService.generateUploadUrl(contentType, filename);

        return ResponseEntity.ok(Map.of(
                "uploadUrl", result.uploadUrl(),
                "s3Key", result.s3Key()
        ));
    }
}
