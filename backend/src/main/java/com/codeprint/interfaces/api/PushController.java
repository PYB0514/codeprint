// Web Push 구독 등록·해제 및 VAPID 공개키 조회 API
package com.codeprint.interfaces.api;

import com.codeprint.domain.notification.PushSubscription;
import com.codeprint.domain.notification.PushSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/push")
@RequiredArgsConstructor
public class PushController {

    private final PushSubscriptionRepository pushSubscriptionRepository;

    @Value("${vapid.public-key:}")
    private String vapidPublicKey;

    record SubscribeRequest(@NotBlank String endpoint, @NotBlank String p256dh, @NotBlank String auth) {}

    // VAPID 공개키 반환 (로그인 불필요)
    @GetMapping("/vapid-public-key")
    public ResponseEntity<Map<String, String>> getVapidPublicKey() {
        return ResponseEntity.ok(Map.of("publicKey", vapidPublicKey));
    }

    // 구독 등록 (이미 있으면 무시)
    @PostMapping("/subscribe")
    public ResponseEntity<Void> subscribe(
            @Valid @RequestBody SubscribeRequest req,
            @AuthenticationPrincipal User user) {
        UUID userId = UUID.fromString(user.getUsername());
        pushSubscriptionRepository.findByUserIdAndEndpoint(userId, req.endpoint())
                .orElseGet(() -> pushSubscriptionRepository.save(
                        PushSubscription.of(userId, req.endpoint(), req.p256dh(), req.auth())
                ));
        return ResponseEntity.ok().build();
    }

    // 구독 해제
    @DeleteMapping("/subscribe")
    public ResponseEntity<Void> unsubscribe(
            @Valid @RequestBody SubscribeRequest req,
            @AuthenticationPrincipal User user) {
        UUID userId = UUID.fromString(user.getUsername());
        pushSubscriptionRepository.deleteByUserIdAndEndpoint(userId, req.endpoint());
        return ResponseEntity.noContent().build();
    }
}
