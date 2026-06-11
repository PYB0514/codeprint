// Web Push 구독 저장소 인터페이스
package com.codeprint.domain.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, UUID> {

    List<PushSubscription> findByUserId(UUID userId);

    Optional<PushSubscription> findByUserIdAndEndpoint(UUID userId, String endpoint);

    void deleteByUserIdAndEndpoint(UUID userId, String endpoint);
}
