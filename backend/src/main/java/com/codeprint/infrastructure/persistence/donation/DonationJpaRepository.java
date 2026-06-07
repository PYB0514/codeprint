// 후원 JPA 저장소
package com.codeprint.infrastructure.persistence.donation;

import com.codeprint.domain.donation.Donation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DonationJpaRepository extends JpaRepository<Donation, UUID> {
    List<Donation> findAllByOrderByCreatedAtDesc();
    boolean existsByOrderId(String orderId);
}
