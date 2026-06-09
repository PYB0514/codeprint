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

    // 이미 처리 완료된 주문인지 확인
    @Override
    public boolean isConfirmed(String orderId) {
        return jpa.findById(orderId)
            .map(r -> "CONFIRMED".equals(r.status))
            .orElse(false);
    }
}
