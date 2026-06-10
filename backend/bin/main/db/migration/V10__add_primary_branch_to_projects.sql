-- 프로젝트별 주요 브랜치 설정 컬럼 추가 (freshness 항시 추적용)
ALTER TABLE projects ADD COLUMN primary_branch VARCHAR(255);
