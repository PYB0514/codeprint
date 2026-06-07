// 후원 결제 확인 및 저장 Application Service
package com.codeprint.application.donation;

import com.codeprint.domain.donation.Donation;
import com.codeprint.domain.donation.DonationRepository;
import com.codeprint.domain.user.User;
import com.codeprint.infrastructure.payment.TossPaymentsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DonationApplicationService {

    private final TossPaymentsService tossPaymentsService;
    private final DonationRepository donationRepository;

    // 토스 결제 승인 후 후원 내역 저장
    @Transactional
    public void confirm(User user, String paymentKey, String orderId, long amount) {
        if (donationRepository.existsByOrderId(orderId)) {
            log.warn("중복 후원 요청 무시: orderId={}", orderId);
            return;
        }

        tossPaymentsService.confirmPayment(paymentKey, orderId, amount);

        Donation donation = Donation.create(user.getId(), user.getUsername(), amount, paymentKey, orderId);
        donationRepository.save(donation);
        log.info("후원 완료: userId={}, amount={}", user.getId(), amount);
    }

    // 전체 후원 내역 조회
    @Transactional(readOnly = true)
    public List<Donation> findAll() {
        return donationRepository.findAllOrderByCreatedAtDesc();
    }
}
