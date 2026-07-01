// 팀 생성·좌석 증가 결제 주문 생성·승인을 오케스트레이션하는 애플리케이션 서비스
package com.codeprint.application.payment;

import com.codeprint.domain.payment.TeamPaymentOrder;
import com.codeprint.domain.payment.TeamPaymentOrderRepository;
import com.codeprint.domain.payment.port.PaymentGatewayPort;
import com.codeprint.domain.payment.port.TeamProvisioningPort;
import com.codeprint.shared.plan.UserPlan;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TeamPaymentApplicationService {

    private final TeamPaymentOrderRepository orderRepository;
    private final PaymentGatewayPort paymentGateway;
    private final TeamProvisioningPort teamProvisioningPort;

    // 신규 팀 생성 결제 주문 생성 — 금액 = 좌석 수 × 좌석당 요금
    public PrepareResult prepareNewTeam(UUID ownerUserId, String teamName, int seats) {
        long amount = (long) seats * UserPlan.DESKTOP.monthlyPricePerSeat();
        String orderId = "team-new-" + UUID.randomUUID();
        TeamPaymentOrder order = TeamPaymentOrder.forNewTeam(orderId, ownerUserId, teamName, seats, amount);
        orderRepository.save(order);
        return new PrepareResult(orderId, amount);
    }

    // 좌석 증가 결제 주문 생성 — 금액 = (새 좌석 수 - 현재 좌석 수) × 좌석당 요금, 팀장만 가능
    public PrepareResult prepareSeatIncrease(UUID ownerUserId, UUID teamId, int newSeats) {
        TeamProvisioningPort.TeamSummary summary = teamProvisioningPort.getTeamSummary(teamId);
        if (!summary.ownerUserId().equals(ownerUserId)) {
            throw new SecurityException("팀장만 좌석을 변경할 수 있습니다.");
        }
        if (newSeats <= summary.seats()) {
            throw new IllegalArgumentException("새 좌석 수는 현재(" + summary.seats() + "석)보다 많아야 합니다.");
        }
        long amount = (long) (newSeats - summary.seats()) * UserPlan.DESKTOP.monthlyPricePerSeat();
        String orderId = "team-seats-" + UUID.randomUUID();
        TeamPaymentOrder order = TeamPaymentOrder.forSeatIncrease(orderId, ownerUserId, teamId, newSeats, amount);
        orderRepository.save(order);
        return new PrepareResult(orderId, amount);
    }

    // 결제 승인 — 멱등·소유권·금액 검증 후 게이트웨이 승인 + 팀 생성 또는 좌석 변경
    public ConfirmOutcome confirm(UUID ownerUserId, String paymentKey, String orderId, long amount) {
        CaptureResult captured = verifyAndCapturePayment(ownerUserId, paymentKey, orderId, amount);
        if (captured.order() == null) {
            return new ConfirmOutcome(captured.rejection(), null);
        }

        TeamPaymentOrder order = captured.order();
        if (order.isNewTeam()) {
            UUID teamId = teamProvisioningPort.createTeam(order.getOwnerUserId(), order.getTeamName(), order.getSeats());
            return new ConfirmOutcome(Result.OK, teamId);
        }
        teamProvisioningPort.changeSeats(order.getTeamId(), order.getSeats());
        return new ConfirmOutcome(Result.OK, order.getTeamId());
    }

    // 멱등·소유권·금액 검증 후 게이트웨이 승인까지 처리 — 거절 시 order=null, rejection에 사유
    private CaptureResult verifyAndCapturePayment(UUID ownerUserId, String paymentKey, String orderId, long amount) {
        if (orderRepository.isConfirmed(orderId)) {
            return new CaptureResult(null, Result.ALREADY_CONFIRMED);
        }

        TeamPaymentOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 주문: " + orderId));

        if (!order.getOwnerUserId().equals(ownerUserId)) {
            return new CaptureResult(null, Result.FORBIDDEN);
        }
        if (order.getAmount() != amount) {
            return new CaptureResult(null, Result.AMOUNT_MISMATCH);
        }

        paymentGateway.confirmPayment(paymentKey, orderId, amount);
        order.confirm(paymentKey);
        orderRepository.save(order);
        return new CaptureResult(order, null);
    }

    private record CaptureResult(TeamPaymentOrder order, Result rejection) {}

    // 결제 주문 생성 결과
    public record PrepareResult(String orderId, long amount) {}

    // 결제 승인 결과 — teamId는 OK일 때만 채워짐(신규 생성 시 새 ID, 좌석 증가 시 기존 ID)
    public record ConfirmOutcome(Result result, UUID teamId) {}

    public enum Result { ALREADY_CONFIRMED, OK, FORBIDDEN, AMOUNT_MISMATCH }
}
