// 사용자 프로필 이미지 및 배경 이미지 업로드/삭제 컨트롤러
package com.codeprint.interfaces.api;

import com.codeprint.domain.user.User;
import com.codeprint.domain.user.UserRepository;
import com.codeprint.infrastructure.storage.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

@RestController
@RequiredArgsConstructor
public class UserImageController {

    private final UserRepository userRepository;
    private final S3Service s3Service;

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif"
    );

    // 프로필 아바타 이미지 업로드
    @PostMapping("/api/users/me/avatar")
    public ResponseEntity<?> uploadAvatar(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal User user) throws IOException {

        validateImage(file);
        String url = s3Service.uploadFile(file, "avatars/");
        user.updateAvatarUrl(url);
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("avatarUrl", url));
    }

    // 프로필 아바타 이미지 삭제
    @DeleteMapping("/api/users/me/avatar")
    public ResponseEntity<?> deleteAvatar(@AuthenticationPrincipal User user) {
        deleteS3File(user.getAvatarUrl());
        user.updateAvatarUrl(null);
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("avatarUrl", (Object) null));
    }

    // 그래프 배경 이미지 업로드
    @PostMapping("/api/users/me/background")
    public ResponseEntity<?> uploadBackground(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal User user) throws IOException {

        validateImage(file);
        String url = s3Service.uploadFile(file, "backgrounds/");
        user.updateGraphBgUrl(url);
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("graphBgUrl", url));
    }

    // 그래프 배경 이미지 삭제
    @DeleteMapping("/api/users/me/background")
    public ResponseEntity<?> deleteBackground(@AuthenticationPrincipal User user) {
        deleteS3File(user.getGraphBgUrl());
        user.updateGraphBgUrl(null);
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("graphBgUrl", (Object) null));
    }

    // 이미지 파일 타입 및 크기 검증
    private void validateImage(MultipartFile file) {
        if (file.isEmpty()) throw new IllegalArgumentException("파일이 비어 있습니다.");
        if (file.getSize() > MAX_FILE_SIZE) throw new IllegalArgumentException("파일 크기는 5MB 이하여야 합니다.");
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("jpg, png, webp, gif 형식만 허용됩니다.");
        }
    }

    // S3 URL에서 키 추출 후 삭제
    private void deleteS3File(String url) {
        if (url == null || url.isBlank()) return;
        try {
            // URL 형식: https://{bucket}.s3.amazonaws.com/{key}
            String key = url.substring(url.indexOf(".amazonaws.com/") + ".amazonaws.com/".length());
            s3Service.deleteObject(key);
        } catch (Exception ignored) {}
    }
}
