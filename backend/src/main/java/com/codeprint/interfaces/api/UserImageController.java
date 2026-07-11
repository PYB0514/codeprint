// 사용자 프로필 이미지 및 배경 이미지 업로드/삭제 컨트롤러
package com.codeprint.interfaces.api;

import com.codeprint.application.user.UserImageService;
import com.codeprint.domain.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class UserImageController {

    private final UserImageService userImageService;

    // 프로필 아바타 이미지 업로드
    @PostMapping("/api/users/me/avatar")
    public ResponseEntity<?> uploadAvatar(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal User user) throws IOException {
        return ResponseEntity.ok(Map.of("avatarUrl", userImageService.uploadAvatar(user.getId(), file)));
    }

    // 프로필 아바타 이미지 삭제
    @DeleteMapping("/api/users/me/avatar")
    public ResponseEntity<?> deleteAvatar(@AuthenticationPrincipal User user) {
        userImageService.deleteAvatar(user.getId());
        return ResponseEntity.ok(Collections.singletonMap("avatarUrl", null));
    }

    // 그래프 배경 이미지 업로드
    @PostMapping("/api/users/me/background")
    public ResponseEntity<?> uploadBackground(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal User user) throws IOException {
        return ResponseEntity.ok(Map.of("graphBgUrl", userImageService.uploadBackground(user.getId(), file)));
    }

    // 그래프 배경 이미지 삭제
    @DeleteMapping("/api/users/me/background")
    public ResponseEntity<?> deleteBackground(@AuthenticationPrincipal User user) {
        userImageService.deleteBackground(user.getId());
        return ResponseEntity.ok(Collections.singletonMap("graphBgUrl", null));
    }
}
