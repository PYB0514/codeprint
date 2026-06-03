// Stripe Webhook 중복 처리 방지를 위한 이벤트 ID 저장소
package com.codeprint.infrastructure.stripe;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface StripeEventJpaRepository extends JpaRepository<StripeEventJpaRepository.StripeEventRecord, String> {

    @Entity
    @Table(name = "stripe_events")
    class StripeEventRecord {
        @Id
        public String eventId;
        public Instant processedAt = Instant.now();

        // 이벤트 ID로 레코드 생성
        public static StripeEventRecord of(String eventId) {
            StripeEventRecord r = new StripeEventRecord();
            r.eventId = eventId;
            return r;
        }
    }
}
