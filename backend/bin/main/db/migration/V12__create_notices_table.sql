-- 운영자 공지사항 테이블
CREATE TABLE IF NOT EXISTS notices (
    id         UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    title      VARCHAR(200) NOT NULL,
    content    TEXT        NOT NULL,
    is_active  BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
