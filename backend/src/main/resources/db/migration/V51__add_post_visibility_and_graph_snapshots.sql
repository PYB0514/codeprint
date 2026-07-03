-- 게시글 공개범위(공개/비공개) + 여러 그래프 스냅샷 첨부 지원

ALTER TABLE posts ADD COLUMN visibility VARCHAR(20) NOT NULL DEFAULT 'PUBLIC';

CREATE TABLE post_graph_snapshots (
    id          UUID PRIMARY KEY,
    post_id     UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    project_id  UUID NOT NULL,
    graph_id    UUID NOT NULL,
    config      JSONB NOT NULL,
    position    INT NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_post_graph_snapshots_post_id ON post_graph_snapshots (post_id);
