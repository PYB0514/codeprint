-- 관리자의 사용자 플랜 변경 감사 로그 (인가된 plan-grant 액션의 누가·언제·왜 기록)
CREATE TABLE plan_grant_logs (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_admin_id  UUID        NOT NULL,
    target_user_id  UUID        NOT NULL,
    old_plan        VARCHAR(20) NOT NULL,
    new_plan        VARCHAR(20) NOT NULL,
    reason          VARCHAR(500) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_plan_grant_logs_created_at ON plan_grant_logs (created_at DESC);
CREATE INDEX idx_plan_grant_logs_target ON plan_grant_logs (target_user_id);
