// 토스페이먼츠 Pro 플랜 결제 준비·승인 컨트롤러
package com.codeprint.interfaces.api;

import com.codeprint.application.payment.PaymentApplicationService;
import com.codeprint.domain.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentApplicationService paymentApplicationService;

    @Value("${toss.client-key:}")
    private String clientKey;

    // Pro 플랜 결제 주문 생성 — 프론트에서 Toss 결제창 호출 시 사용
    @PostMapping("/toss/prepare")
    public ResponseEntity<Map<String, Object>> prepare(@AuthenticationPrincipal User user) {
        PaymentApplicationService.PrepareResult result = paymentApplicationService.prepare(user.getId());

        return ResponseEntity.ok(Map.of(
            "orderId", result.orderId(),
            "amount", result.amount(),
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

        PaymentApplicationService.ConfirmOutcome outcome =
                paymentApplicationService.confirm(user.getId(), req.paymentKey(), req.orderId(), req.amount());

        return switch (outcome) {
            case ALREADY_CONFIRMED -> ResponseEntity.ok(Map.of("result", "already_confirmed"));
            case FORBIDDEN -> ResponseEntity.status(403).body(Map.of("error", "접근 권한 없음"));
            case AMOUNT_MISMATCH -> ResponseEntity.badRequest().body(Map.of("error", "결제 금액 불일치"));
            case OK -> ResponseEntity.ok(Map.of("result", "ok"));
        };
    }

    record ConfirmRequest(String paymentKey, String orderId, long amount) {}
}
