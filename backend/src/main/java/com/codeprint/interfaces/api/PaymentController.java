// 토스페이먼츠 Pro 플랜 결제 준비·승인 컨트롤러
package com.codeprint.interfaces.api;

import com.codeprint.domain.payment.TossPaymentOrder;
import com.codeprint.domain.payment.TossPaymentOrderRepository;
import com.codeprint.domain.user.User;
import com.codeprint.domain.user.UserRepository;
import com.codeprint.infrastructure.payment.TossPaymentsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    static final long PRO_AMOUNT = 9900L;

    private final TossPaymentsService tossPaymentsService;
    private final TossPaymentOrderRepository orderRepository;
    private final UserRepository userRepository;

    @Value("${toss.client-key:}")
    private String clientKey;

    // Pro 플랜 결제 주문 생성 — 프론트에서 Toss 결제창 호출 시 사용
    @PostMapping("/toss/prepare")
    public ResponseEntity<Map<String, Object>> prepare(@AuthenticationPrincipal User user) {
        String orderId = "pro-" + UUID.randomUUID();
        TossPaymentOrder order = new TossPaymentOrder(orderId, user.getId(), PRO_AMOUNT);
        orderRepository.save(order);

        return ResponseEntity.ok(Map.of(
            "orderId", orderId,
            "amount", PRO_AMOUNT,
            "orderName", "Codeprint Pro",
            "customerName", user.getUsername(),
            "customerKey", user.getId().toString(),
            "clientKey", clientKey
        ));
    }

    // 토스 결제 승인 — 프론트 리다이렉트 후 호출
    @PostMapping("/toss/confirm")
    public ResponseEntity<Map<String, String>> confirm(
            @AuthenticationPrincipal User user,
            @RequestBody ConfirmRequest req) {

        if (orderRepository.isConfirmed(req.orderId())) {
            return ResponseEntity.ok(Map.of("result", "already_confirmed"));
        }

        TossPaymentOrder order = orderRepository.findById(req.orderId())
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 주문: " + req.orderId()));

        if (!order.getUserId().equals(user.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "접근 권한 없음"));
        }

        if (order.getAmount() != req.amount()) {
            return ResponseEntity.badRequest().body(Map.of("error", "결제 금액 불일치"));
        }

        tossPaymentsService.confirmPayment(req.paymentKey(), req.orderId(), req.amount());
        order.confirm(req.paymentKey());
        orderRepository.save(order);

        userRepository.findById(user.getId()).ifPresent(u -> {
            u.upgradeToPro();
            userRepository.save(u);
            log.info("Pro 업그레이드 완료: userId={}", u.getId());
        });

        return ResponseEntity.ok(Map.of("result", "ok"));
    }

    record ConfirmRequest(String paymentKey, String orderId, long amount) {}
}
