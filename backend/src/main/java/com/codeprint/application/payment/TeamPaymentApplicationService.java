// 팀 생성·좌석 증가 결제 주문 생성·승인을 오케스트레이션하는 애플리케이션 서비스
package com.codeprint.application.payment;

import com.codeprint.domain.payment.TeamPaymentOrder;
import com.codeprint.domain.payment.TeamPaymentOrderRepository;
import com.codeprint.domain.payment.port.PaymentGatewayPort;
import com.codeprint.domain.payment.port.TeamProvisioningPort;
import com.codeprint.shared.plan.UserPlan;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    // 좌석 증가 결제 주문 생성 — 금액 = (새 좌석 수 - 현재 좌석 수) × 좌석당 요금, 팀장만 가능.
    // 주문엔 증분만 저장(TeamPaymentOrder.forSeatIncrease 참조) — 이 시점의 "현재 좌석 수"는 검증·안내용 스냅샷일 뿐,
    // 실제 반영은 confirm 시점에 증분만큼 원자적으로 더해지므로 다른 주문과 동시에 확정돼도 어긋나지 않는다.
    public PrepareResult prepareSeatIncrease(UUID ownerUserId, UUID teamId, int newSeats) {
        TeamProvisioningPort.TeamSummary summary = teamProvisioningPort.getTeamSummary(teamId);
        if (!summary.ownerUserId().equals(ownerUserId)) {
            throw new SecurityException("팀장만 좌석을 변경할 수 있습니다.");
        }
        if (newSeats <= summary.seats()) {
            throw new IllegalArgumentException("새 좌석 수는 현재(" + summary.seats() + "석)보다 많아야 합니다.");
        }
        int deltaSeats = newSeats - summary.seats();
        long amount = (long) deltaSeats * UserPlan.DESKTOP.monthlyPricePerSeat();
        String orderId = "team-seats-" + UUID.randomUUID();
        TeamPaymentOrder order = TeamPaymentOrder.forSeatIncrease(orderId, ownerUserId, teamId, deltaSeats, amount);
        orderRepository.save(order);
        return new PrepareResult(orderId, amount);
    }

    // 결제 승인 — 행 잠금 조회로 동시 요청 직렬화 + 멱등·소유권·금액 검증 후 게이트웨이 승인 + 팀 생성 또는 좌석 변경
    @Transactional
    public ConfirmOutcome confirm(UUID ownerUserId, String paymentKey, String orderId, long amount) {
        TeamPaymentOrder order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 주문: " + orderId));

        if (order.getStatus() == TeamPaymentOrder.Status.CONFIRMED) {
            return new ConfirmOutcome(Result.ALREADY_CONFIRMED, null);
        }
        if (!order.getOwnerUserId().equals(ownerUserId)) {
            return new ConfirmOutcome(Result.FORBIDDEN, null);
        }
        if (order.getAmount() != amount) {
            return new ConfirmOutcome(Result.AMOUNT_MISMATCH, null);
        }

        paymentGateway.confirmPayment(paymentKey, orderId, amount);
        order.confirm(paymentKey);
        orderRepository.save(order);
        return new ConfirmOutcome(Result.OK, provision(order));
    }

    // 신규 팀 생성 또는 기존 팀 좌석 증가 실행 — 결과 teamId 반환.
    // 좌석 증가 주문의 getSeats()는 증분(delta)이라 increaseSeatsBy로 원자적으로 더한다(절대치 지정 아님).
    private UUID provision(TeamPaymentOrder order) {
        if (order.isNewTeam()) {
            return teamProvisioningPort.createTeam(order.getOwnerUserId(), order.getTeamName(), order.getSeats());
        }
        teamProvisioningPort.increaseSeatsBy(order.getTeamId(), order.getSeats());
        return order.getTeamId();
    }

    // 결제 주문 생성 결과
    public record PrepareResult(String orderId, long amount) {}

    // 결제 승인 결과 — teamId는 OK일 때만 채워짐(신규 생성 시 새 ID, 좌석 증가 시 기존 ID)
    public record ConfirmOutcome(Result result, UUID teamId) {}

    public enum Result { ALREADY_CONFIRMED, OK, FORBIDDEN, AMOUNT_MISMATCH }
}
