-- 게시글·댓글 신고 저장 테이블
CREATE TABLE reports (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_id  UUID        NOT NULL,
    target_type  VARCHAR(20) NOT NULL,
    target_id    UUID        NOT NULL,
    reason       TEXT        NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
