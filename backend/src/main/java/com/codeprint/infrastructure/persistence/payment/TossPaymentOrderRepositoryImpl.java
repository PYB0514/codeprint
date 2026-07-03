// 토스 결제 주문 Repository JPA 구현체
package com.codeprint.infrastructure.persistence.payment;

import com.codeprint.domain.payment.TossPaymentOrder;
import com.codeprint.domain.payment.TossPaymentOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class TossPaymentOrderRepositoryImpl implements TossPaymentOrderRepository {

    private final TossPaymentOrderJpaRepository jpa;

    // 새 결제 주문 저장
    @Override
    public void save(TossPaymentOrder order) {
        jpa.save(TossPaymentOrderJpaRepository.TossPaymentOrderRecord.from(order));
    }

    // orderId로 주문 조회
    @Override
    public Optional<TossPaymentOrder> findById(String orderId) {
        return jpa.findById(orderId).map(r -> r.toDomain());
    }

    // 행 잠금을 건 상태로 조회 (confirm 동시 요청 직렬화 전용, 호출자가 @Transactional이어야 잠금 유지)
    @Override
    public Optional<TossPaymentOrder> findByIdForUpdate(String orderId) {
        return jpa.findByOrderId(orderId).map(r -> r.toDomain());
    }
}
