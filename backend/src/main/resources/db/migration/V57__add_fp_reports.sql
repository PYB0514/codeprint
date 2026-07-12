-- 사용자가 "이 경고는 오탐이다"라고 명시적으로 신고한 기록 — 숨기기(warning_suppressions)와 달리 학습 신호로 쓰임
-- fingerprint = SHA-256(type + "|" + message) — warning_suppressions와 동일 컨벤션

CREATE TABLE fp_reports (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id    UUID         NOT NULL,
    fingerprint   VARCHAR(64)  NOT NULL,
    warning_type  VARCHAR(50),
    reporter_id   UUID         NOT NULL,
    reason        TEXT,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_fpr_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    CONSTRAINT uq_fpr_project_fingerprint_reporter UNIQUE (project_id, fingerprint, reporter_id)
);

CREATE INDEX idx_fpr_project ON fp_reports(project_id);
