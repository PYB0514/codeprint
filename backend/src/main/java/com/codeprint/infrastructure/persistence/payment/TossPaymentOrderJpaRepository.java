// 토스 결제 주문 JPA 엔티티 및 Spring Data Repository
package com.codeprint.infrastructure.persistence.payment;

import com.codeprint.domain.payment.TossPaymentOrder;
import jakarta.persistence.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface TossPaymentOrderJpaRepository extends JpaRepository<TossPaymentOrderJpaRepository.TossPaymentOrderRecord, String> {

    @Entity
    @Table(name = "toss_payment_orders")
    class TossPaymentOrderRecord {

        @Id
        @Column(name = "order_id")
        public String orderId;

        @Column(name = "user_id", nullable = false)
        public UUID userId;

        @Column(name = "amount", nullable = false)
        public long amount;

        @Column(name = "status", nullable = false)
        public String status;

        @Column(name = "payment_key")
        public String paymentKey;

        @Column(name = "created_at", nullable = false)
        public Instant createdAt;

        @Column(name = "confirmed_at")
        public Instant confirmedAt;

        // 도메인 객체에서 레코드 생성
        public static TossPaymentOrderRecord from(TossPaymentOrder order) {
            TossPaymentOrderRecord r = new TossPaymentOrderRecord();
            r.orderId = order.getOrderId();
            r.userId = order.getUserId();
            r.amount = order.getAmount();
            r.status = order.getStatus().name();
            r.paymentKey = order.getPaymentKey();
            r.createdAt = order.getCreatedAt();
            r.confirmedAt = order.getConfirmedAt();
            return r;
        }

        // 레코드를 도메인 객체로 변환
        public TossPaymentOrder toDomain() {
            TossPaymentOrder order = new TossPaymentOrder(orderId, userId, amount);
            if ("CONFIRMED".equals(status) && paymentKey != null) {
                order.confirm(paymentKey);
            }
            return order;
        }
    }
}
