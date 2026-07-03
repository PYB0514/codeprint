// Toss 결제 승인 동시 요청 통합 테스트 — 실 Postgres 행 잠금(SELECT ... FOR UPDATE)으로 confirm() 중복 실행을 막는지 검증
// 로컬은 docker compose의 codeprint-db, CI는 ci.yml의 postgres 서비스에 접속(둘 다 postgres/1234/codeprint).
package com.codeprint.application.payment;

import com.codeprint.domain.payment.TossPaymentOrder;
import com.codeprint.domain.payment.TossPaymentOrderRepository;
import com.codeprint.domain.payment.port.PaymentGatewayPort;
import com.codeprint.domain.payment.port.UserUpgradePort;
import com.codeprint.infrastructure.persistence.payment.TossPaymentOrderJpaRepository;
import com.codeprint.infrastructure.persistence.payment.TossPaymentOrderRepositoryImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

// NOT_SUPPORTED — @DataJpaTest 기본 롤백 트랜잭션을 끄고 각 스레드가 독립 커넥션/트랜잭션으로 실 행 잠금을 걸도록 함
// (기본 롤백을 켠 채로는 setup의 save()가 커밋되지 않아 워커 스레드가 주문을 찾지 못함). 대신 @AfterEach로 직접 정리.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TossPaymentOrderRepositoryImpl.class, PaymentApplicationService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/codeprint",
        "spring.datasource.username=postgres",
        "spring.datasource.password=1234",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.flyway.validate-on-migrate=false"
})
class PaymentApplicationServiceConcurrencyIntegrationTest {

    @Autowired
    private PaymentApplicationService service;

    @Autowired
    private TossPaymentOrderRepository orderRepository;

    @Autowired
    private TossPaymentOrderJpaRepository jpaRepository;

    @MockitoBean
    private PaymentGatewayPort paymentGateway;

    @MockitoBean
    private UserUpgradePort userUpgradePort;

    private String orderId;

    @AfterEach
    void cleanup() {
        if (orderId != null) {
            jpaRepository.deleteById(orderId);
        }
    }

    // 동일 orderId로 confirm()을 동시에 2번 호출해도 게이트웨이 승인·Pro 승급은 정확히 1회만 발생해야 한다
    @Test
    @DisplayName("confirm 동시 요청 — 행 잠금으로 직렬화되어 게이트웨이 승인·Pro 승급이 정확히 1회만 호출된다")
    void concurrentConfirm_isSerializedByRowLock() throws Exception {
        UUID userId = UUID.randomUUID();
        orderId = "concurrency-test-" + UUID.randomUUID();
        orderRepository.save(new TossPaymentOrder(orderId, userId, 9900L));

        int threadCount = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);

        List<Future<PaymentApplicationService.ConfirmOutcome>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                return service.confirm(userId, "pk-shared", orderId, 9900L);
            }));
        }
        ready.await(5, TimeUnit.SECONDS);
        start.countDown();

        List<PaymentApplicationService.ConfirmOutcome> outcomes = new ArrayList<>();
        for (Future<PaymentApplicationService.ConfirmOutcome> f : futures) {
            outcomes.add(f.get(10, TimeUnit.SECONDS));
        }
        pool.shutdown();

        assertThat(outcomes).containsExactlyInAnyOrder(
                PaymentApplicationService.ConfirmOutcome.OK,
                PaymentApplicationService.ConfirmOutcome.ALREADY_CONFIRMED);
        verify(paymentGateway, times(1)).confirmPayment(anyString(), eq(orderId), eq(9900L));
        verify(userUpgradePort, times(1)).upgradeToPro(userId);
    }
}
