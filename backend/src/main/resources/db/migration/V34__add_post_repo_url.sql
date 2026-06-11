-- 커뮤니티 게시글에 연결된 공개 프로젝트의 레포 URL 컬럼 추가
ALTER TABLE posts ADD COLUMN IF NOT EXISTS repo_url TEXT;
