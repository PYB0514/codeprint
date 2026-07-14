-- 갤러리 리포가 매일 같은 커밋으로 재분석되는 낭비를 막기 위해 마지막 분석 시점의 커밋 SHA를 저장

ALTER TABLE featured_repos
    ADD COLUMN last_analyzed_commit_sha VARCHAR(50);
