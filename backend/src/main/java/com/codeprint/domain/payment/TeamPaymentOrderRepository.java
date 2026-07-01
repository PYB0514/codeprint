// 팀 결제 주문 도메인 Repository 인터페이스
package com.codeprint.domain.payment;

import java.util.Optional;

public interface TeamPaymentOrderRepository {

    // 새 결제 주문 저장
    void save(TeamPaymentOrder order);

    // orderId로 주문 조회
    Optional<TeamPaymentOrder> findById(String orderId);

    // 이미 처리 완료된 주문인지 확인
    boolean isConfirmed(String orderId);
}
