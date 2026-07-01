-- 팀 생성/좌석 증가 결제 주문 기록 테이블 (team_id가 NULL이면 신규 팀 생성 주문)

CREATE TABLE team_payment_orders (
    order_id      VARCHAR(100) PRIMARY KEY,
    owner_user_id UUID         NOT NULL,
    team_id       UUID,
    team_name     VARCHAR(100),
    seats         INT          NOT NULL,
    amount        BIGINT       NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    payment_key   VARCHAR(200),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    confirmed_at  TIMESTAMPTZ
);
