// PaymentApplicationService 결제 승인 분기 단위 테스트 — 멱등·소유권·금액 검증 회귀 방지
package com.codeprint.application.payment;

import com.codeprint.domain.payment.TossPaymentOrder;
import com.codeprint.domain.payment.TossPaymentOrderRepository;
import com.codeprint.domain.payment.port.PaymentGatewayPort;
import com.codeprint.domain.payment.port.UserUpgradePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentApplicationServiceTest {

    @Mock private TossPaymentOrderRepository orderRepository;
    @Mock private PaymentGatewayPort paymentGateway;
    @Mock private UserUpgradePort userUpgradePort;

    private PaymentApplicationService service;

    @BeforeEach
    void setUp() {
        service = new PaymentApplicationService(orderRepository, paymentGateway, userUpgradePort);
    }

    @Test
    @DisplayName("prepare — PRO_AMOUNT로 주문 생성·저장, pro- 접두 orderId 반환")
    void prepare_createsOrder() {
        UUID userId = UUID.randomUUID();

        var result = service.prepare(userId);

        assertThat(result.amount()).isEqualTo(PaymentApplicationService.PRO_AMOUNT);
        assertThat(result.orderId()).startsWith("pro-");
        verify(orderRepository).save(any(TossPaymentOrder.class));
    }

    @Test
    @DisplayName("confirm — 이미 승인된 주문이면 ALREADY_CONFIRMED, 게이트웨이·승급 미호출")
    void confirm_alreadyConfirmed() {
        when(orderRepository.isConfirmed("order-1")).thenReturn(true);

        var outcome = service.confirm(UUID.randomUUID(), "pk", "order-1", 9900L);

        assertThat(outcome).isEqualTo(PaymentApplicationService.ConfirmOutcome.ALREADY_CONFIRMED);
        verify(paymentGateway, never()).confirmPayment(any(), any(), anyLong());
        verify(userUpgradePort, never()).upgradeToPro(any());
    }

    @Test
    @DisplayName("confirm — 존재하지 않는 주문이면 IllegalArgumentException")
    void confirm_orderNotFound() {
        when(orderRepository.isConfirmed("order-x")).thenReturn(false);
        when(orderRepository.findById("order-x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirm(UUID.randomUUID(), "pk", "order-x", 9900L))
                .isInstanceOf(IllegalArgumentException.class);
        verify(paymentGateway, never()).confirmPayment(any(), any(), anyLong());
        verify(userUpgradePort, never()).upgradeToPro(any());
    }

    @Test
    @DisplayName("confirm — 주문 소유자가 아니면 FORBIDDEN, 게이트웨이·승급 미호출")
    void confirm_notOwner() {
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        when(orderRepository.isConfirmed("order-2")).thenReturn(false);
        when(orderRepository.findById("order-2"))
                .thenReturn(Optional.of(new TossPaymentOrder("order-2", owner, 9900L)));

        var outcome = service.confirm(other, "pk", "order-2", 9900L);

        assertThat(outcome).isEqualTo(PaymentApplicationService.ConfirmOutcome.FORBIDDEN);
        verify(paymentGateway, never()).confirmPayment(any(), any(), anyLong());
        verify(userUpgradePort, never()).upgradeToPro(any());
    }

    @Test
    @DisplayName("confirm — 금액 불일치면 AMOUNT_MISMATCH, 게이트웨이·승급 미호출")
    void confirm_amountMismatch() {
        UUID userId = UUID.randomUUID();
        when(orderRepository.isConfirmed("order-3")).thenReturn(false);
        when(orderRepository.findById("order-3"))
                .thenReturn(Optional.of(new TossPaymentOrder("order-3", userId, 9900L)));

        var outcome = service.confirm(userId, "pk", "order-3", 5000L);

        assertThat(outcome).isEqualTo(PaymentApplicationService.ConfirmOutcome.AMOUNT_MISMATCH);
        verify(paymentGateway, never()).confirmPayment(any(), any(), anyLong());
        verify(userUpgradePort, never()).upgradeToPro(any());
    }

    @Test
    @DisplayName("confirm — 정상 흐름이면 게이트웨이 승인·주문 저장·Pro 승급 후 OK")
    void confirm_success() {
        UUID userId = UUID.randomUUID();
        TossPaymentOrder order = new TossPaymentOrder("order-4", userId, 9900L);
        when(orderRepository.isConfirmed("order-4")).thenReturn(false);
        when(orderRepository.findById("order-4")).thenReturn(Optional.of(order));

        var outcome = service.confirm(userId, "pk-4", "order-4", 9900L);

        assertThat(outcome).isEqualTo(PaymentApplicationService.ConfirmOutcome.OK);
        verify(paymentGateway).confirmPayment("pk-4", "order-4", 9900L);
        verify(orderRepository).save(order);
        verify(userUpgradePort).upgradeToPro(userId);
        assertThat(order.getStatus()).isEqualTo(TossPaymentOrder.Status.CONFIRMED);
        assertThat(order.getPaymentKey()).isEqualTo("pk-4");
    }
}
