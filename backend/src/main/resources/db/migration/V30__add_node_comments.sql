-- 그래프 노드별 코멘트를 저장하는 테이블
CREATE TABLE node_comments (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    graph_id   UUID        NOT NULL REFERENCES graphs(id) ON DELETE CASCADE,
    node_id    TEXT        NOT NULL,
    user_id    UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content    TEXT        NOT NULL,
    created_at TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE INDEX idx_node_comments_graph_node ON node_comments (graph_id, node_id);
