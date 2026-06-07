// 후원 저장소 JPA 구현체
package com.codeprint.infrastructure.persistence.donation;

import com.codeprint.domain.donation.Donation;
import com.codeprint.domain.donation.DonationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class DonationRepositoryImpl implements DonationRepository {

    private final DonationJpaRepository jpa;

    // 후원 내역 저장
    @Override
    public void save(Donation donation) {
        jpa.save(donation);
    }

    // 전체 후원 내역을 최신순으로 조회
    @Override
    public List<Donation> findAllOrderByCreatedAtDesc() {
        return jpa.findAllByOrderByCreatedAtDesc();
    }

    // orderId 중복 여부 확인
    @Override
    public boolean existsByOrderId(String orderId) {
        return jpa.existsByOrderId(orderId);
    }
}
