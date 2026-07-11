// 사용자 아바타·배경 이미지 업로드/삭제 애플리케이션 서비스
package com.codeprint.application.user;

import com.codeprint.domain.user.User;
import com.codeprint.domain.user.UserRepository;
import com.codeprint.infrastructure.storage.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class UserImageService {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif"
    );

    private final UserRepository userRepository;
    private final S3Service s3Service;

    // 아바타 이미지 업로드 후 presigned URL 반환
    public String uploadAvatar(UUID userId, MultipartFile file) throws IOException {
        validateImage(file);
        User user = getUser(userId);
        String url = s3Service.uploadFile(file, "avatars/");
        user.updateAvatarUrl(url);
        userRepository.save(user);
        return s3Service.toPresignedUrl(url);
    }

    // 아바타 이미지 삭제
    public void deleteAvatar(UUID userId) {
        User user = getUser(userId);
        deleteS3File(user.getAvatarUrl());
        user.updateAvatarUrl(null);
        userRepository.save(user);
    }

    // 그래프 배경 이미지 업로드 후 presigned URL 반환
    public String uploadBackground(UUID userId, MultipartFile file) throws IOException {
        validateImage(file);
        User user = getUser(userId);
        String url = s3Service.uploadFile(file, "backgrounds/");
        user.updateGraphBgUrl(url);
        userRepository.save(user);
        return s3Service.toPresignedUrl(url);
    }

    // 그래프 배경 이미지 삭제
    public void deleteBackground(UUID userId) {
        User user = getUser(userId);
        deleteS3File(user.getGraphBgUrl());
        user.updateGraphBgUrl(null);
        userRepository.save(user);
    }

    private User getUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
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
