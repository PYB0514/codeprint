-- 일별 서비스 지표 스냅샷 — 일일 다이제스트의 전일 대비 비교·이상 감지 토대
CREATE TABLE daily_stats (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    stat_date        DATE        NOT NULL UNIQUE,
    new_users        INT         NOT NULL DEFAULT 0,
    active_users     INT         NOT NULL DEFAULT 0,
    new_projects     INT         NOT NULL DEFAULT 0,
    analyses_total   INT         NOT NULL DEFAULT 0,
    analyses_failed  INT         NOT NULL DEFAULT 0,
    payments_count   INT         NOT NULL DEFAULT 0,
    payments_amount  BIGINT      NOT NULL DEFAULT 0,
    new_feedback     INT         NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
