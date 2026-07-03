// DonationApplicationService 단위 테스트 — 결제 멱등성·승인 순서·게이트웨이 실패 분기 회귀 방지
package com.codeprint.application.donation;

import com.codeprint.domain.donation.Donation;
import com.codeprint.domain.donation.DonationRepository;
import com.codeprint.domain.donation.port.PaymentGatewayPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DonationApplicationServiceTest {

    @Mock
    private PaymentGatewayPort paymentGateway;

    @Mock
    private DonationRepository donationRepository;

    @InjectMocks
    private DonationApplicationService service;

    private final UUID userId = UUID.randomUUID();

    @Test
    @DisplayName("결제 확인 — 신규 주문이면 게이트웨이 승인 후 저장")
    void confirm_newOrder_confirmsThenSaves() {
        when(donationRepository.existsByOrderId("order-1")).thenReturn(false);

        service.confirm(userId, "u", "pk-1", "order-1", 5000L);

        verify(paymentGateway).confirmPayment("pk-1", "order-1", 5000L);
        ArgumentCaptor<Donation> captor = ArgumentCaptor.forClass(Donation.class);
        verify(donationRepository).save(captor.capture());
        assertThat(captor.getValue().getOrderId()).isEqualTo("order-1");
        assertThat(captor.getValue().getAmount()).isEqualTo(5000L);
    }

    @Test
    @DisplayName("결제 확인 — 중복 주문이면 멱등 무시(게이트웨이·저장 안 함)")
    void confirm_duplicateOrder_idempotentSkip() {
        when(donationRepository.existsByOrderId("order-1")).thenReturn(true);

        service.confirm(userId, "u", "pk-1", "order-1", 5000L);

        verify(paymentGateway, never()).confirmPayment(anyString(), anyString(), anyLong());
        verify(donationRepository, never()).save(any(Donation.class));
    }

    @Test
    @DisplayName("결제 확인 — 게이트웨이 승인 실패면 저장 안 함(예외 전파)")
    void confirm_gatewayFails_doesNotSave() {
        when(donationRepository.existsByOrderId("order-1")).thenReturn(false);
        doThrow(new IllegalStateException("승인 실패"))
                .when(paymentGateway).confirmPayment("pk-1", "order-1", 5000L);

        assertThatThrownBy(() -> service.confirm(userId, "u", "pk-1", "order-1", 5000L))
                .isInstanceOf(IllegalStateException.class);
        verify(donationRepository, never()).save(any(Donation.class));
    }

    @Test
    @DisplayName("결제 확인 — existsByOrderId 통과 후 동시요청 경합으로 DB UNIQUE 제약 위반 시 예외 없이 멱등 무시")
    void confirm_raceConditionOnSave_idempotentSkip() {
        when(donationRepository.existsByOrderId("order-1")).thenReturn(false);
        doThrow(new DataIntegrityViolationException("duplicate key"))
                .when(donationRepository).save(any(Donation.class));

        service.confirm(userId, "u", "pk-1", "order-1", 5000L);

        verify(paymentGateway).confirmPayment("pk-1", "order-1", 5000L);
    }

    @Test
    @DisplayName("전체 후원 조회 — 레포지토리 위임")
    void findAll_delegates() {
        Donation d = Donation.create(userId, "u", 1000L, "pk", "o");
        when(donationRepository.findAllOrderByCreatedAtDesc()).thenReturn(List.of(d));

        assertThat(service.findAll()).containsExactly(d);
    }
}
