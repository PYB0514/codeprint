-- posts 테이블에 조회수 컬럼 추가
ALTER TABLE posts ADD COLUMN IF NOT EXISTS view_count BIGINT NOT NULL DEFAULT 0;
