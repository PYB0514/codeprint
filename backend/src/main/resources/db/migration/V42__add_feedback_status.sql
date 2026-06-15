-- 문의 처리 상태 컬럼 추가 — 관리자가 처리 완료를 표시해 미처리 백로그를 추적
ALTER TABLE feedbacks ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'OPEN';
