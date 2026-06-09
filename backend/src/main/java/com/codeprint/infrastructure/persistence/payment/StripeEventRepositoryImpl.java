// Stripe 이벤트 Repository 구현체 — JPA 위임
package com.codeprint.infrastructure.persistence.payment;

import com.codeprint.domain.payment.StripeEventRepository;
import com.codeprint.infrastructure.stripe.StripeEventJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class StripeEventRepositoryImpl implements StripeEventRepository {

    private final StripeEventJpaRepository stripeEventJpa;

    // 이미 처리된 이벤트인지 확인
    @Override
    public boolean existsById(String eventId) {
        return stripeEventJpa.existsById(eventId);
    }

    // 처리 완료 이벤트로 기록
    @Override
    public void markProcessed(String eventId) {
        stripeEventJpa.save(StripeEventJpaRepository.StripeEventRecord.of(eventId));
    }
}
