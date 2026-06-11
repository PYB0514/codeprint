-- 사용자 문의/피드백 저장 테이블
CREATE TABLE feedbacks (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID,
    category    VARCHAR(20) NOT NULL,
    title       VARCHAR(200) NOT NULL,
    content     TEXT        NOT NULL,
    email       VARCHAR(200),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
