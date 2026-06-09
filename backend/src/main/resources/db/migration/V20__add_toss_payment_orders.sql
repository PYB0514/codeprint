-- 토스페이먼츠 Pro 플랜 결제 주문 기록 테이블

CREATE TABLE toss_payment_orders (
    order_id     VARCHAR(100) PRIMARY KEY,
    user_id      UUID         NOT NULL,
    amount       BIGINT       NOT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    payment_key  VARCHAR(200),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    confirmed_at TIMESTAMPTZ
);
