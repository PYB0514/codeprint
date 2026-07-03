// 팀 결제 주문 JPA 엔티티 및 Spring Data Repository
package com.codeprint.infrastructure.persistence.payment;

import com.codeprint.domain.payment.TeamPaymentOrder;
import jakarta.persistence.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TeamPaymentOrderJpaRepository extends JpaRepository<TeamPaymentOrderJpaRepository.TeamPaymentOrderRecord, String> {

    // confirm 동시 요청 직렬화용 — SELECT ... FOR UPDATE
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TeamPaymentOrderRecord> findByOrderId(String orderId);

    @Entity
    @Table(name = "team_payment_orders")
    class TeamPaymentOrderRecord {

        @Id
        @Column(name = "order_id")
        public String orderId;

        @Column(name = "owner_user_id", nullable = false)
        public UUID ownerUserId;

        @Column(name = "team_id")
        public UUID teamId;

        @Column(name = "team_name")
        public String teamName;

        @Column(name = "seats", nullable = false)
        public int seats;

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
        public static TeamPaymentOrderRecord from(TeamPaymentOrder order) {
            TeamPaymentOrderRecord r = new TeamPaymentOrderRecord();
            r.orderId = order.getOrderId();
            r.ownerUserId = order.getOwnerUserId();
            r.teamId = order.getTeamId();
            r.teamName = order.getTeamName();
            r.seats = order.getSeats();
            r.amount = order.getAmount();
            r.status = order.getStatus().name();
            r.paymentKey = order.getPaymentKey();
            r.createdAt = order.getCreatedAt();
            r.confirmedAt = order.getConfirmedAt();
            return r;
        }

        // 레코드를 도메인 객체로 변환
        public TeamPaymentOrder toDomain() {
            return TeamPaymentOrder.restore(orderId, ownerUserId, teamId, teamName, seats, amount,
                    TeamPaymentOrder.Status.valueOf(status), paymentKey, createdAt, confirmedAt);
        }
    }
}
