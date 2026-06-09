// 로컬 개발 전용 — 테스트 더미 유저 JWT 발급 엔드포인트
package com.codeprint.interfaces.api;

import com.codeprint.infrastructure.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/dev")
@RequiredArgsConstructor
@Profile("local")
public class DevController {

    private final JwtTokenProvider jwtTokenProvider;

    // V18 마이그레이션으로 삽입된 테스트 더미 유저의 JWT 발급
    @GetMapping("/test-token")
    public ResponseEntity<Map<String, String>> getTestToken() {
        UUID testUserId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        String token = jwtTokenProvider.generateToken(testUserId, "testuser@codeprint.dev", "USER");
        return ResponseEntity.ok(Map.of(
                "token", token,
                "userId", testUserId.toString(),
                "username", "테스트유저"
        ));
    }
}
