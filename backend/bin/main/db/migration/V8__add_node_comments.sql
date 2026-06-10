-- 노드 코멘트 저장 테이블 — 그래프 뷰에서 특정 노드에 메모를 남기는 기능
CREATE TABLE node_comments (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    graph_id    UUID NOT NULL REFERENCES graphs(id) ON DELETE CASCADE,
    node_id     UUID NOT NULL REFERENCES nodes(id) ON DELETE CASCADE,
    user_id     UUID NOT NULL,
    content     TEXT NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_node_comments_graph_node ON node_comments(graph_id, node_id);
