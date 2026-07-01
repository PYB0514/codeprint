// 팀 결제 주문 Repository JPA 구현체
package com.codeprint.infrastructure.persistence.payment;

import com.codeprint.domain.payment.TeamPaymentOrder;
import com.codeprint.domain.payment.TeamPaymentOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class TeamPaymentOrderRepositoryImpl implements TeamPaymentOrderRepository {

    private final TeamPaymentOrderJpaRepository jpa;

    // 새 결제 주문 저장
    @Override
    public void save(TeamPaymentOrder order) {
        jpa.save(TeamPaymentOrderJpaRepository.TeamPaymentOrderRecord.from(order));
    }

    // orderId로 주문 조회
    @Override
    public Optional<TeamPaymentOrder> findById(String orderId) {
        return jpa.findById(orderId).map(TeamPaymentOrderJpaRepository.TeamPaymentOrderRecord::toDomain);
    }

    // 이미 처리 완료된 주문인지 확인
    @Override
    public boolean isConfirmed(String orderId) {
        return jpa.findById(orderId)
            .map(r -> "CONFIRMED".equals(r.status))
            .orElse(false);
    }
}
