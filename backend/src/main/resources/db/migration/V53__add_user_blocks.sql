-- 쪽지 발신 차단 관계 저장 테이블
CREATE TABLE user_blocks (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    blocker_id  UUID        NOT NULL,
    blocked_id  UUID        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (blocker_id, blocked_id)
);
