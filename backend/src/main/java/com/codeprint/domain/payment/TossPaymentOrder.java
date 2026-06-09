// 토스페이먼츠 Pro 결제 주문 도메인 객체
package com.codeprint.domain.payment;

import java.time.Instant;
import java.util.UUID;

public class TossPaymentOrder {

    public enum Status { PENDING, CONFIRMED, FAILED }

    private final String orderId;
    private final UUID userId;
    private final long amount;
    private Status status;
    private String paymentKey;
    private final Instant createdAt;
    private Instant confirmedAt;

    public TossPaymentOrder(String orderId, UUID userId, long amount) {
        this.orderId = orderId;
        this.userId = userId;
        this.amount = amount;
        this.status = Status.PENDING;
        this.createdAt = Instant.now();
    }

    // 승인 완료 처리
    public void confirm(String paymentKey) {
        this.status = Status.CONFIRMED;
        this.paymentKey = paymentKey;
        this.confirmedAt = Instant.now();
    }

    public String getOrderId() { return orderId; }
    public UUID getUserId() { return userId; }
    public long getAmount() { return amount; }
    public Status getStatus() { return status; }
    public String getPaymentKey() { return paymentKey; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getConfirmedAt() { return confirmedAt; }
}
