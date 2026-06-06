-- users 테이블에 role(어드민 구분)과 enabled(계정 활성화) 컬럼 추가
ALTER TABLE users ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER';
ALTER TABLE users ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE;
