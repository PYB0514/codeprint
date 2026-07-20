// 팀 생성·좌석 증가 결제 주문 도메인 객체 — teamId가 null이면 신규 팀 생성 주문
package com.codeprint.domain.payment;

import java.time.Instant;
import java.util.UUID;

public class TeamPaymentOrder {

    public enum Status { PENDING, CONFIRMED, FAILED }

    private final String orderId;
    private final UUID ownerUserId;
    private final UUID teamId;
    private final String teamName;
    // 신규 팀 주문: 초기 좌석 수(절대치). 좌석 증가 주문: 추가 구매 좌석 수(증분) — forSeatIncrease 참조.
    private final int seats;
    private final long amount;
    private Status status;
    private String paymentKey;
    private final Instant createdAt;
    private Instant confirmedAt;

    private TeamPaymentOrder(String orderId, UUID ownerUserId, UUID teamId, String teamName, int seats, long amount) {
        this.orderId = orderId;
        this.ownerUserId = ownerUserId;
        this.teamId = teamId;
        this.teamName = teamName;
        this.seats = seats;
        this.amount = amount;
        this.status = Status.PENDING;
        this.createdAt = Instant.now();
    }

    // 신규 팀 생성 주문
    public static TeamPaymentOrder forNewTeam(String orderId, UUID ownerUserId, String teamName, int seats, long amount) {
        return new TeamPaymentOrder(orderId, ownerUserId, null, teamName, seats, amount);
    }

    // 기존 팀 좌석 증가 주문 — deltaSeats는 절대 목표치가 아니라 "추가로 구매하는 좌석 수"(증분).
    // 절대치로 저장하면 확정(confirm) 시점 사이 다른 주문이 끼어들 때 지불액과 최종 좌석 수가 어긋난다(TOCTOU) —
    // provision()에서 항상 증분만큼 더하도록(increaseSeatsBy) 설계해 확정 순서와 무관하게 결제액=증가분이 유지된다.
    public static TeamPaymentOrder forSeatIncrease(String orderId, UUID ownerUserId, UUID teamId, int deltaSeats, long amount) {
        return new TeamPaymentOrder(orderId, ownerUserId, teamId, null, deltaSeats, amount);
    }

    // DB 레코드 복원용 (JPA 매핑 계층에서만 사용)
    public static TeamPaymentOrder restore(String orderId, UUID ownerUserId, UUID teamId, String teamName,
                                            int seats, long amount, Status status, String paymentKey,
                                            Instant createdAt, Instant confirmedAt) {
        TeamPaymentOrder o = new TeamPaymentOrder(orderId, ownerUserId, teamId, teamName, seats, amount);
        o.status = status;
        o.paymentKey = paymentKey;
        o.confirmedAt = confirmedAt;
        return o;
    }

    // 승인 완료 처리
    public void confirm(String paymentKey) {
        this.status = Status.CONFIRMED;
        this.paymentKey = paymentKey;
        this.confirmedAt = Instant.now();
    }

    // teamId가 없으면 신규 팀 생성 주문
    public boolean isNewTeam() {
        return teamId == null;
    }

    public String getOrderId() { return orderId; }
    public UUID getOwnerUserId() { return ownerUserId; }
    public UUID getTeamId() { return teamId; }
    public String getTeamName() { return teamName; }
    public int getSeats() { return seats; }
    public long getAmount() { return amount; }
    public Status getStatus() { return status; }
    public String getPaymentKey() { return paymentKey; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getConfirmedAt() { return confirmedAt; }
}
