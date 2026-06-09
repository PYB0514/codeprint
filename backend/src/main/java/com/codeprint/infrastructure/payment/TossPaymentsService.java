// 토스페이먼츠 결제 승인 API 호출 서비스
package com.codeprint.infrastructure.payment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Base64;
import java.util.Map;

@Slf4j
@Service
public class TossPaymentsService {

    private final RestClient restClient;
    private final String encodedSecretKey;

    public TossPaymentsService(@Value("${toss.secret-key:}") String secretKey) {
        this.restClient = RestClient.create();
        this.encodedSecretKey = Base64.getEncoder().encodeToString((secretKey + ":").getBytes());
    }

    // 토스 결제 승인 API 호출 — 실패 시 예외 발생
    public void confirmPayment(String paymentKey, String orderId, long amount) {
        Map<String, Object> body = Map.of(
            "paymentKey", paymentKey,
            "orderId", orderId,
            "amount", amount
        );

        try {
            restClient.post()
                .uri("https://api.tosspayments.com/v1/payments/confirm")
                .header("Authorization", "Basic " + encodedSecretKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();

            log.info("토스 결제 승인 완료: orderId={}, amount={}", orderId, amount);
        } catch (RestClientResponseException e) {
            log.error("토스 결제 승인 실패: orderId={}, status={}, body={}", orderId, e.getStatusCode(), e.getResponseBodyAsString());
            throw new IllegalStateException("결제 승인 실패: " + e.getResponseBodyAsString());
        }
    }
}
