// Stripe Checkout 세션 생성 및 Webhook 검증 서비스
package com.codeprint.infrastructure.stripe;

import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class StripePaymentService {

    @Value("${stripe.secret-key}")
    private String secretKey;

    @Value("${stripe.pro-price-id}")
    private String proPriceId;

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    // Stripe SDK에 시크릿 키 초기화
    @PostConstruct
    public void init() {
        Stripe.apiKey = secretKey;
    }

    // Pro 플랜 Checkout 세션을 생성하고 결제 URL 반환
    public String createCheckoutSession(UUID userId, String userEmail) throws Exception {
        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setCustomerEmail(userEmail)
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setPrice(proPriceId)
                        .setQuantity(1L)
                        .build())
                .setSuccessUrl(frontendUrl + "/payment/success?session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(frontendUrl + "/payment/cancel")
                .putMetadata("userId", userId.toString())
                .build();

        Session session = Session.create(params);
        return session.getUrl();
    }

    // Stripe Webhook 서명을 검증하고 Event 반환
    public Event constructEvent(String payload, String sigHeader) throws SignatureVerificationException {
        return Webhook.constructEvent(payload, sigHeader, webhookSecret);
    }
}
