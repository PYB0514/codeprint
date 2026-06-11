-- 노드에 사용자 정의 레이블 및 메모 컬럼 추가
ALTER TABLE nodes ADD COLUMN IF NOT EXISTS user_label TEXT;
ALTER TABLE nodes ADD COLUMN IF NOT EXISTS user_note  TEXT;
