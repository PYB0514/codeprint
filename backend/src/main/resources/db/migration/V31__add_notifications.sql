-- 인앱 알림을 저장하는 테이블
CREATE TABLE notifications (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type       TEXT        NOT NULL,
    message    TEXT        NOT NULL,
    link       TEXT,
    is_read    BOOLEAN     NOT NULL DEFAULT false,
    created_at TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE INDEX idx_notifications_user_id ON notifications (user_id, created_at DESC);
