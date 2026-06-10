-- 유저 팔로우 관계 테이블
CREATE TABLE IF NOT EXISTS user_follows (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    follower_id UUID NOT NULL,
    following_id UUID NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_follows UNIQUE (follower_id, following_id),
    CONSTRAINT chk_no_self_follow CHECK (follower_id <> following_id)
);

CREATE INDEX IF NOT EXISTS idx_user_follows_follower   ON user_follows (follower_id);
CREATE INDEX IF NOT EXISTS idx_user_follows_following  ON user_follows (following_id);
