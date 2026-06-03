-- Stripe Webhook 멱등성 처리용 이벤트 기록 테이블 추가 + github_access_token 암호화 길이 확장

CREATE TABLE stripe_events (
    event_id     VARCHAR(100) PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE users
    ALTER COLUMN github_access_token TYPE VARCHAR(500);
