-- BYOK 키별 failover 우선순위(낮을수록 우선) — 기존 행은 등록 시각순으로 채움
ALTER TABLE user_ai_keys ADD COLUMN priority INT NOT NULL DEFAULT 0;

UPDATE user_ai_keys t
SET priority = ranked.rn - 1
FROM (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY user_id ORDER BY created_at) AS rn
    FROM user_ai_keys
) ranked
WHERE t.id = ranked.id;
