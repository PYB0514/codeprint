-- 사용자 활동 시각 컬럼 추가 — DAU 집계를 토큰 발급 프록시에서 실제 활동 기준으로 정밀화
ALTER TABLE users ADD COLUMN last_active_at TIMESTAMPTZ;
