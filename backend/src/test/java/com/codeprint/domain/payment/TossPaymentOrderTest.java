// TossPaymentOrder 단위 테스트 — 초기 PENDING·confirm 상태 전이 회귀 방지
package com.codeprint.domain.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TossPaymentOrderTest {

    private final UUID userId = UUID.randomUUID();

    @Test
    @DisplayName("생성 직후 — PENDING 상태, paymentKey·confirmedAt 미설정")
    void newOrder_isPending() {
        TossPaymentOrder order = new TossPaymentOrder("order-1", userId, 9_900L);

        assertThat(order.getStatus()).isEqualTo(TossPaymentOrder.Status.PENDING);
        assertThat(order.getPaymentKey()).isNull();
        assertThat(order.getConfirmedAt()).isNull();
        assertThat(order.getAmount()).isEqualTo(9_900L);
        assertThat(order.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("confirm — PENDING에서 CONFIRMED로 전이하며 paymentKey·confirmedAt 설정")
    void confirm_transitionsToConfirmed() {
        TossPaymentOrder order = new TossPaymentOrder("order-1", userId, 9_900L);

        order.confirm("pay_key_123");

        assertThat(order.getStatus()).isEqualTo(TossPaymentOrder.Status.CONFIRMED);
        assertThat(order.getPaymentKey()).isEqualTo("pay_key_123");
        assertThat(order.getConfirmedAt()).isNotNull();
    }
}
