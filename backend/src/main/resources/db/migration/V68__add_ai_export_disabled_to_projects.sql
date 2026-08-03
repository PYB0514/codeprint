-- AI 컨텍스트 내보내기(역할 명세서/기능명세) 차단 토글 — 코드를 외부 LLM으로 보내는 걸 원천 금지하고 싶은 프로젝트용(DLP)

ALTER TABLE projects
    ADD COLUMN ai_export_disabled BOOLEAN NOT NULL DEFAULT FALSE;
