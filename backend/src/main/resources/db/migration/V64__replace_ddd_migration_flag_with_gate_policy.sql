-- "DDD로 마이그레이션" 단방향 boolean을 자동/DDD/레이어드 3택 게이트 정책으로 대체(레이어드 방향 강제도 대칭 지원)
ALTER TABLE projects ADD COLUMN gate_policy VARCHAR(10) NOT NULL DEFAULT 'AUTO';
UPDATE projects SET gate_policy = 'DDD' WHERE ddd_migration_enabled = TRUE;
ALTER TABLE projects DROP COLUMN ddd_migration_enabled;
