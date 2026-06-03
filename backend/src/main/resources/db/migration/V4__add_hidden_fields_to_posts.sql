-- 게시글 공유 시 숨길 레이어/그룹/노드 정보 컬럼 추가
ALTER TABLE posts
    ADD COLUMN hidden_layers     JSONB NOT NULL DEFAULT '[]',
    ADD COLUMN hidden_groups     JSONB NOT NULL DEFAULT '[]',
    ADD COLUMN hidden_node_names JSONB NOT NULL DEFAULT '[]';
