// TeamPaymentApplicationService 결제 승인 분기 단위 테스트 — 멱등·소유권·금액 검증 + 신규팀/좌석증가 분기 회귀 방지
package com.codeprint.application.payment;

import com.codeprint.domain.payment.TeamPaymentOrder;
import com.codeprint.domain.payment.TeamPaymentOrderRepository;
import com.codeprint.domain.payment.port.PaymentGatewayPort;
import com.codeprint.domain.payment.port.TeamProvisioningPort;
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
class TeamPaymentApplicationServiceTest {

    @Mock private TeamPaymentOrderRepository orderRepository;
    @Mock private PaymentGatewayPort paymentGateway;
    @Mock private TeamProvisioningPort teamProvisioningPort;

    private TeamPaymentApplicationService service;

    @BeforeEach
    void setUp() {
        service = new TeamPaymentApplicationService(orderRepository, paymentGateway, teamProvisioningPort);
    }

    // --- prepareNewTeam ---

    @Test
    @DisplayName("prepareNewTeam — 좌석 수 × 4,900원으로 주문 생성·저장")
    void prepareNewTeam_createsOrder() {
        UUID owner = UUID.randomUUID();

        var result = service.prepareNewTeam(owner, "team", 5);

        assertThat(result.amount()).isEqualTo(5L * 4_900);
        assertThat(result.orderId()).startsWith("team-new-");
        verify(orderRepository).save(any(TeamPaymentOrder.class));
    }

    // --- prepareSeatIncrease ---

    @Test
    @DisplayName("prepareSeatIncrease — 차액(newSeats-현재)×4,900원으로 주문 생성")
    void prepareSeatIncrease_chargesDeltaOnly() {
        UUID owner = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        when(teamProvisioningPort.getTeamSummary(teamId)).thenReturn(new TeamProvisioningPort.TeamSummary(owner, 5));

        var result = service.prepareSeatIncrease(owner, teamId, 8);

        assertThat(result.amount()).isEqualTo(3L * 4_900);
        verify(orderRepository).save(any(TeamPaymentOrder.class));
    }

    @Test
    @DisplayName("prepareSeatIncrease — 팀장이 아니면 SecurityException")
    void prepareSeatIncrease_notOwner_rejected() {
        UUID owner = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        when(teamProvisioningPort.getTeamSummary(teamId)).thenReturn(new TeamProvisioningPort.TeamSummary(owner, 5));

        assertThatThrownBy(() -> service.prepareSeatIncrease(UUID.randomUUID(), teamId, 8))
                .isInstanceOf(SecurityException.class);
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("prepareSeatIncrease — 새 좌석 수가 현재 이하면 IllegalArgumentException")
    void prepareSeatIncrease_notIncreasing_rejected() {
        UUID owner = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        when(teamProvisioningPort.getTeamSummary(teamId)).thenReturn(new TeamProvisioningPort.TeamSummary(owner, 5));

        assertThatThrownBy(() -> service.prepareSeatIncrease(owner, teamId, 5))
                .isInstanceOf(IllegalArgumentException.class);
        verify(orderRepository, never()).save(any());
    }

    // --- confirm: 공통 검증 분기 ---

    @Test
    @DisplayName("confirm — 이미 승인된 주문이면 ALREADY_CONFIRMED, 게이트웨이·프로비저닝 미호출")
    void confirm_alreadyConfirmed() {
        TeamPaymentOrder confirmed = TeamPaymentOrder.forNewTeam("order-1", UUID.randomUUID(), "t", 5, 24500L);
        confirmed.confirm("pk-existing");
        when(orderRepository.findByIdForUpdate("order-1")).thenReturn(Optional.of(confirmed));

        var outcome = service.confirm(UUID.randomUUID(), "pk", "order-1", 9900L);

        assertThat(outcome.result()).isEqualTo(TeamPaymentApplicationService.Result.ALREADY_CONFIRMED);
        verify(paymentGateway, never()).confirmPayment(any(), any(), anyLong());
        verify(teamProvisioningPort, never()).createTeam(any(), any(), anyInt());
    }

    @Test
    @DisplayName("confirm — 존재하지 않는 주문이면 IllegalArgumentException")
    void confirm_orderNotFound() {
        when(orderRepository.findByIdForUpdate("order-x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirm(UUID.randomUUID(), "pk", "order-x", 9900L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("confirm — 주문 소유자가 아니면 FORBIDDEN")
    void confirm_notOwner() {
        UUID owner = UUID.randomUUID();
        when(orderRepository.findByIdForUpdate("order-2"))
                .thenReturn(Optional.of(TeamPaymentOrder.forNewTeam("order-2", owner, "t", 5, 24500L)));

        var outcome = service.confirm(UUID.randomUUID(), "pk", "order-2", 24500L);

        assertThat(outcome.result()).isEqualTo(TeamPaymentApplicationService.Result.FORBIDDEN);
        verify(paymentGateway, never()).confirmPayment(any(), any(), anyLong());
    }

    @Test
    @DisplayName("confirm — 금액 불일치면 AMOUNT_MISMATCH")
    void confirm_amountMismatch() {
        UUID owner = UUID.randomUUID();
        when(orderRepository.findByIdForUpdate("order-3"))
                .thenReturn(Optional.of(TeamPaymentOrder.forNewTeam("order-3", owner, "t", 5, 24500L)));

        var outcome = service.confirm(owner, "pk", "order-3", 1000L);

        assertThat(outcome.result()).isEqualTo(TeamPaymentApplicationService.Result.AMOUNT_MISMATCH);
        verify(paymentGateway, never()).confirmPayment(any(), any(), anyLong());
    }

    // --- confirm: 신규 팀 생성 분기 ---

    @Test
    @DisplayName("confirm — 신규 팀 주문이면 게이트웨이 승인 후 팀 생성, teamId 반환")
    void confirm_newTeam_success() {
        UUID owner = UUID.randomUUID();
        UUID newTeamId = UUID.randomUUID();
        TeamPaymentOrder order = TeamPaymentOrder.forNewTeam("order-4", owner, "myteam", 5, 24500L);
        when(orderRepository.findByIdForUpdate("order-4")).thenReturn(Optional.of(order));
        when(teamProvisioningPort.createTeam(owner, "myteam", 5)).thenReturn(newTeamId);

        var outcome = service.confirm(owner, "pk-4", "order-4", 24500L);

        assertThat(outcome.result()).isEqualTo(TeamPaymentApplicationService.Result.OK);
        assertThat(outcome.teamId()).isEqualTo(newTeamId);
        verify(paymentGateway).confirmPayment("pk-4", "order-4", 24500L);
        verify(orderRepository).save(order);
        verify(teamProvisioningPort).createTeam(owner, "myteam", 5);
        verify(teamProvisioningPort, never()).changeSeats(any(), anyInt());
    }

    // --- confirm: 좌석 증가 분기 ---

    @Test
    @DisplayName("confirm — 좌석 증가 주문이면 게이트웨이 승인 후 좌석 변경, 기존 teamId 반환")
    void confirm_seatIncrease_success() {
        UUID owner = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        TeamPaymentOrder order = TeamPaymentOrder.forSeatIncrease("order-5", owner, teamId, 8, 14700L);
        when(orderRepository.findByIdForUpdate("order-5")).thenReturn(Optional.of(order));

        var outcome = service.confirm(owner, "pk-5", "order-5", 14700L);

        assertThat(outcome.result()).isEqualTo(TeamPaymentApplicationService.Result.OK);
        assertThat(outcome.teamId()).isEqualTo(teamId);
        verify(paymentGateway).confirmPayment("pk-5", "order-5", 14700L);
        verify(teamProvisioningPort).changeSeats(teamId, 8);
        verify(teamProvisioningPort, never()).createTeam(any(), any(), anyInt());
    }
}
