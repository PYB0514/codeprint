-- 예외(IGNORE) 규칙 추가/제거 이력 — 팀 거버넌스용 감사 로그(누가 언제 어떤 규칙을 바꿨는지)
CREATE TABLE architecture_intent_audit_log (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id     UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    user_id        UUID NOT NULL,
    username       VARCHAR(100) NOT NULL,
    action         VARCHAR(10) NOT NULL,
    rule_type      VARCHAR(50),
    rule_from      VARCHAR(200),
    rule_to        VARCHAR(200),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_architecture_intent_audit_log_project_id ON architecture_intent_audit_log(project_id);
