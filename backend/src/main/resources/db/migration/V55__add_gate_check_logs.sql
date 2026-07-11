-- PR 게이트 체크 결과 기록 — 지표 대시보드(북극성/실적) 집계용 데이터 소스
CREATE TABLE gate_check_logs (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id     UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    pr_number      INT NOT NULL,
    state          VARCHAR(20) NOT NULL,
    high_count     INT NOT NULL,
    warning_count  INT NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_gate_check_logs_project_id ON gate_check_logs(project_id);
CREATE INDEX idx_gate_check_logs_created_at ON gate_check_logs(created_at);
