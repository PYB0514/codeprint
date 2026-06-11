-- Web Push 구독 정보를 저장하는 테이블
CREATE TABLE push_subscriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    endpoint TEXT NOT NULL,
    p256dh TEXT NOT NULL,
    auth TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (user_id, endpoint)
);
CREATE INDEX idx_push_subscriptions_user_id ON push_subscriptions(user_id);
