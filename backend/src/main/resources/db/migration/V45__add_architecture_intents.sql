-- 프로젝트별 사용자 선언 의도 아키텍처 — 모듈(경로 글로브)과 FORBID 의존 규칙을 JSON으로 저장
CREATE TABLE architecture_intents (
    project_id  UUID        PRIMARY KEY REFERENCES projects(id) ON DELETE CASCADE,
    intent_json TEXT        NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
