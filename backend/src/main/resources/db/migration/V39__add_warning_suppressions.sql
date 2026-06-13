-- 사용자가 프로젝트 단위로 숨긴(suppress) 경고를 저장하는 테이블
-- fingerprint = SHA-256(type + "|" + message) — 재분석으로 그래프가 바뀌어도 동일 경고면 동일 fingerprint

CREATE TABLE warning_suppressions (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id    UUID         NOT NULL,
    fingerprint   VARCHAR(64)  NOT NULL,
    warning_type  VARCHAR(50),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_ws_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    CONSTRAINT uq_ws_project_fingerprint UNIQUE (project_id, fingerprint)
);

CREATE INDEX idx_ws_project ON warning_suppressions(project_id);
