// 토스페이먼츠 결제 주문 도메인 Repository 인터페이스
package com.codeprint.domain.payment;

import java.util.Optional;
import java.util.UUID;

public interface TossPaymentOrderRepository {

    // 새 결제 주문 저장
    void save(TossPaymentOrder order);

    // orderId로 주문 조회
    Optional<TossPaymentOrder> findById(String orderId);

    // 행 잠금을 건 상태로 조회 (confirm 동시 요청 직렬화 전용)
    Optional<TossPaymentOrder> findByIdForUpdate(String orderId);
}
