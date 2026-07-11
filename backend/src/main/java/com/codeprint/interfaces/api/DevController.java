// 로컬 개발 전용 — 테스트 더미 유저 JWT 발급, 크론 수동 트리거 엔드포인트
package com.codeprint.interfaces.api;

import com.codeprint.application.featured.FeaturedRepoService;
import com.codeprint.application.user.AuthTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/dev")
@RequiredArgsConstructor
@Profile("local")
public class DevController {

    private final AuthTokenService authTokenService;
    private final FeaturedRepoService featuredRepoService;

    // V18 마이그레이션으로 삽입된 테스트 더미 유저의 JWT 발급
    @GetMapping("/test-token")
    public ResponseEntity<Map<String, String>> getTestToken() {
        UUID testUserId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        String token = authTokenService.issueAccessToken(testUserId, "testuser@codeprint.dev", "USER");
        return ResponseEntity.ok(Map.of(
                "token", token,
                "userId", testUserId.toString(),
                "username", "테스트유저"
        ));
    }

    // 오늘의 공개레포 크론(매일 06:00 KST)을 기다리지 않고 즉시 실행
    @PostMapping("/trigger-featured-repos")
    public ResponseEntity<Void> triggerFeaturedRepos() {
        featuredRepoService.refreshDailyFeatured();
        return ResponseEntity.ok().build();
    }
}
