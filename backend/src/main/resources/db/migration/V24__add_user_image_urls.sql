-- 사용자 프로필 이미지 및 그래프 배경 이미지 URL 저장 컬럼 추가
ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_url VARCHAR(500);
ALTER TABLE users ADD COLUMN IF NOT EXISTS graph_bg_url VARCHAR(500);
