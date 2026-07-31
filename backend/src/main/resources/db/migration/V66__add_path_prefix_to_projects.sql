-- 국소분석(디렉토리 스코프) 설정값 — 프로젝트당 하나의 스코프를 영속(설정 화면에서 변경 가능)

ALTER TABLE projects
    ADD COLUMN path_prefix VARCHAR(500);
