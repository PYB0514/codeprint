// Stripe Webhook 중복 처리 방지 도메인 Repository 인터페이스
package com.codeprint.domain.payment;

public interface StripeEventRepository {

    // 이미 처리된 이벤트인지 확인
    boolean existsById(String eventId);

    // 처리 완료 이벤트로 기록
    void markProcessed(String eventId);
}
