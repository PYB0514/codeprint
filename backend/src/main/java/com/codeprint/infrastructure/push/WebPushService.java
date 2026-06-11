// Web Push 알림을 VAPID 방식으로 발송하는 서비스
package com.codeprint.infrastructure.push;

import com.codeprint.domain.notification.PushSubscription;
import com.codeprint.domain.notification.PushSubscriptionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Subscription;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.security.Security;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class WebPushService {

    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final ObjectMapper objectMapper;
    private final PushService pushService;

    public WebPushService(
            PushSubscriptionRepository pushSubscriptionRepository,
            ObjectMapper objectMapper,
            @Value("${vapid.public-key}") String publicKey,
            @Value("${vapid.private-key}") String privateKey,
            @Value("${vapid.subject}") String subject) throws Exception {
        this.pushSubscriptionRepository = pushSubscriptionRepository;
        this.objectMapper = objectMapper;

        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }

        if (publicKey.isBlank() || privateKey.isBlank()) {
            this.pushService = null;
            log.warn("VAPID 키가 설정되지 않아 Web Push 기능이 비활성화됩니다.");
            return;
        }

        this.pushService = new PushService(publicKey, privateKey, subject);
    }

    // 사용자의 모든 구독에 알림 전송 (비동기)
    @Async
    public void sendToUser(UUID userId, String title, String body) {
        if (pushService == null) return;

        List<PushSubscription> subs = pushSubscriptionRepository.findByUserId(userId);
        for (PushSubscription sub : subs) {
            try {
                String payload = objectMapper.writeValueAsString(Map.of(
                        "title", title,
                        "body", body
                ));
                Subscription subscription = new Subscription(
                        sub.getEndpoint(),
                        new Subscription.Keys(sub.getP256dh(), sub.getAuth())
                );
                pushService.send(new Notification(subscription, payload));
            } catch (Exception e) {
                log.warn("Web Push 전송 실패 (userId={}, endpoint={}): {}", userId, sub.getEndpoint(), e.getMessage());
                // 만료된 구독은 제거
                if (e.getMessage() != null && e.getMessage().contains("410")) {
                    pushSubscriptionRepository.deleteByUserIdAndEndpoint(userId, sub.getEndpoint());
                }
            }
        }
    }
}
