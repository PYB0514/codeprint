-- users 테이블에 GitHub OAuth access token 컬럼 추가
ALTER TABLE users ADD COLUMN github_access_token VARCHAR(255);
