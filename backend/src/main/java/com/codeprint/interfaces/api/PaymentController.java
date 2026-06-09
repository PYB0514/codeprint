// Stripe 결제 Checkout 세션 생성 및 Webhook 처리 컨트롤러
package com.codeprint.interfaces.api;

import com.codeprint.domain.payment.StripeEventRepository;
import com.codeprint.domain.user.User;
import com.codeprint.domain.user.UserRepository;
import com.codeprint.infrastructure.stripe.StripePaymentService;
import com.stripe.model.Event;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final StripePaymentService stripePaymentService;
    private final UserRepository userRepository;
    private final StripeEventRepository stripeEventRepository;

    // Pro 플랜 Checkout 세션을 생성하고 결제 URL 반환
    @PostMapping("/checkout")
    public ResponseEntity<Map<String, String>> createCheckout(@AuthenticationPrincipal User user) {
        try {
            String url = stripePaymentService.createCheckoutSession(user.getId(), user.getEmail());
            return ResponseEntity.ok(Map.of("url", url));
        } catch (Exception e) {
            log.error("Checkout 세션 생성 실패: userId={}", user.getId(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", "결제 세션 생성에 실패했습니다."));
        }
    }

    // Stripe Webhook 이벤트 수신 및 플랜 업데이트 처리
    @PostMapping("/webhook")
    public ResponseEntity<String> webhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {
        Event event;
        try {
            event = stripePaymentService.constructEvent(payload, sigHeader);
        } catch (Exception e) {
            log.warn("Webhook 서명 검증 실패: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Invalid signature");
        }

        if (stripeEventRepository.existsById(event.getId())) {
            log.debug("중복 Webhook 이벤트 무시: {}", event.getId());
            return ResponseEntity.ok("ok");
        }
        stripeEventRepository.markProcessed(event.getId());

        switch (event.getType()) {
            case "checkout.session.completed" -> {
                Session session = (Session) event.getDataObjectDeserializer()
                        .getObject().orElse(null);
                if (session != null && session.getMetadata() != null) {
                    String userIdStr = session.getMetadata().get("userId");
                    if (userIdStr != null) upgradeUserToPro(UUID.fromString(userIdStr));
                }
            }
            case "customer.subscription.deleted" -> {
                Subscription sub = (Subscription) event.getDataObjectDeserializer()
                        .getObject().orElse(null);
                if (sub != null && sub.getMetadata() != null) {
                    String userIdStr = sub.getMetadata().get("userId");
                    if (userIdStr != null) downgradeUserToFree(UUID.fromString(userIdStr));
                }
            }
            default -> log.debug("미처리 Webhook 이벤트: {}", event.getType());
        }

        return ResponseEntity.ok("ok");
    }

    // 사용자 플랜을 PRO로 업그레이드
    private void upgradeUserToPro(UUID userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.upgradeToPro();
            userRepository.save(user);
            log.info("Pro 업그레이드 완료: userId={}", userId);
        });
    }

    // 사용자 플랜을 FREE로 다운그레이드
    private void downgradeUserToFree(UUID userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.downgradeToFree();
            userRepository.save(user);
            log.info("Free 다운그레이드 완료: userId={}", userId);
        });
    }
}
