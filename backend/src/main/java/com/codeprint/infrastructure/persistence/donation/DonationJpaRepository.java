// 후원 JPA 저장소
package com.codeprint.infrastructure.persistence.donation;

import com.codeprint.domain.donation.Donation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DonationJpaRepository extends JpaRepository<Donation, UUID> {
    // 전체 후원 최신순 조회
    List<Donation> findAllByOrderByCreatedAtDesc();
    // 주문ID 존재 여부 확인
    boolean existsByOrderId(String orderId);
}
