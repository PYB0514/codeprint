-- 유저 간 1:1 쪽지를 저장하는 테이블
CREATE TABLE direct_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sender_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    receiver_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_dm_receiver_id ON direct_messages(receiver_id, created_at DESC);
CREATE INDEX idx_dm_sender_id ON direct_messages(sender_id, created_at DESC);
