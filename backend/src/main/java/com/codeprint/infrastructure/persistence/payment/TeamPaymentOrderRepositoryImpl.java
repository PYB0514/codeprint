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

    // 행 잠금을 건 상태로 조회 (confirm 동시 요청 직렬화 전용, 호출자가 @Transactional이어야 잠금 유지)
    @Override
    public Optional<TeamPaymentOrder> findByIdForUpdate(String orderId) {
        return jpa.findByOrderId(orderId).map(TeamPaymentOrderJpaRepository.TeamPaymentOrderRecord::toDomain);
    }
}
