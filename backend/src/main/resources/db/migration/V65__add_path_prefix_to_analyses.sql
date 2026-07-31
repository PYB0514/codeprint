-- 국소분석(디렉토리 스코프) 지원 — 분석 범위를 레포 전체가 아닌 특정 하위 경로로 한정

ALTER TABLE analyses
    ADD COLUMN path_prefix VARCHAR(500);
