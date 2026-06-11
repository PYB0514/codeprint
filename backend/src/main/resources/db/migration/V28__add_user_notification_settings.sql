-- 유저별 알림 수신 설정을 저장하는 테이블
CREATE TABLE user_notification_settings (
    user_id     UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    team_chat   BOOLEAN NOT NULL DEFAULT TRUE,
    dm          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
