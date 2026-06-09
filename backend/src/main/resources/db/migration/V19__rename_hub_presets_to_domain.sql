-- 기존에 저장된 '허브' 프리셋 이름을 '도메인'으로 변경, layoutPreset 값도 hub→domain 수정
UPDATE graph_view_presets
SET name = REPLACE(name, '허브', '도메인')
WHERE name LIKE '%허브%';

UPDATE graph_view_presets
SET config = REPLACE(config::text, '"layoutPreset":"hub"', '"layoutPreset":"domain"')::jsonb
WHERE config::text LIKE '%"layoutPreset":"hub"%';
