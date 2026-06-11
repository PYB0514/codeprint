-- V8 스키마(UUID node_id FK)와의 충돌 해소 후 올바른 스키마로 재생성
DROP TABLE IF EXISTS node_comments CASCADE;

CREATE TABLE node_comments (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    graph_id   UUID        NOT NULL REFERENCES graphs(id) ON DELETE CASCADE,
    node_id    TEXT        NOT NULL,
    user_id    UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content    TEXT        NOT NULL,
    created_at TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE INDEX idx_node_comments_graph_node ON node_comments (graph_id, node_id);
