// 후원 내역 저장소 인터페이스
package com.codeprint.domain.donation;

import java.util.List;

public interface DonationRepository {
    void save(Donation donation);
    List<Donation> findAllOrderByCreatedAtDesc();
    boolean existsByOrderId(String orderId);
}
