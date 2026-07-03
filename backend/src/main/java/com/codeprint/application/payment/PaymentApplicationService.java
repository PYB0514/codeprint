// Pro 플랜 결제 주문 생성·승인을 오케스트레이션하는 애플리케이션 서비스
package com.codeprint.application.payment;

import com.codeprint.domain.payment.TossPaymentOrder;
import com.codeprint.domain.payment.TossPaymentOrderRepository;
import com.codeprint.domain.payment.port.PaymentGatewayPort;
import com.codeprint.domain.payment.port.UserUpgradePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentApplicationService {

    public static final long PRO_AMOUNT = 9900L;

    private final TossPaymentOrderRepository orderRepository;
    private final PaymentGatewayPort paymentGateway;
    private final UserUpgradePort userUpgradePort;

    // Pro 플랜 결제 주문 생성 — 생성된 주문 식별자·금액 반환
    public PrepareResult prepare(UUID userId) {
        String orderId = "pro-" + UUID.randomUUID();
        TossPaymentOrder order = new TossPaymentOrder(orderId, userId, PRO_AMOUNT);
        orderRepository.save(order);
        return new PrepareResult(orderId, PRO_AMOUNT);
    }

    // 결제 승인 — 행 잠금 조회로 동시 요청 직렬화 + 멱등(이미 승인)·소유권·금액 검증 후 게이트웨이 승인 + Pro 승급
    @Transactional
    public ConfirmOutcome confirm(UUID userId, String paymentKey, String orderId, long amount) {
        TossPaymentOrder order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 주문: " + orderId));

        if (order.getStatus() == TossPaymentOrder.Status.CONFIRMED) {
            return ConfirmOutcome.ALREADY_CONFIRMED;
        }
        if (!order.getUserId().equals(userId)) {
            return ConfirmOutcome.FORBIDDEN;
        }
        if (order.getAmount() != amount) {
            return ConfirmOutcome.AMOUNT_MISMATCH;
        }

        paymentGateway.confirmPayment(paymentKey, orderId, amount);
        order.confirm(paymentKey);
        orderRepository.save(order);
        userUpgradePort.upgradeToPro(userId);
        return ConfirmOutcome.OK;
    }

    // 결제 주문 생성 결과
    public record PrepareResult(String orderId, long amount) {}

    // 결제 승인 결과 — 컨트롤러가 HTTP 응답으로 매핑
    public enum ConfirmOutcome { ALREADY_CONFIRMED, OK, FORBIDDEN, AMOUNT_MISMATCH }
}
