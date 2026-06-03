-- 분석 시점의 브랜치 최신 커밋 SHA 저장 컬럼 추가
ALTER TABLE analyses ADD COLUMN last_commit_sha VARCHAR(40);
