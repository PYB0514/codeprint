-- 사용자가 DDD 구조로 전환을 원할 때 자동감지와 무관하게 DDD 게이트 규칙을 켜는 플래그
ALTER TABLE projects ADD COLUMN ddd_migration_enabled BOOLEAN NOT NULL DEFAULT FALSE;
