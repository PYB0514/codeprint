// Payment 도메인에서 외부 결제 게이트웨이 승인을 호출하는 포트 (PG사 구현 비노출)
package com.codeprint.domain.payment.port;

public interface PaymentGatewayPort {

    // 결제 승인 요청 — 실패 시 예외 발생
    void confirmPayment(String paymentKey, String orderId, long amount);
}
