-- 노드별 커스텀 스타일 저장 테이블 — 배경색 등 사용자 정의 시각화 속성
CREATE TABLE IF NOT EXISTS node_styles (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    graph_id   UUID NOT NULL REFERENCES graphs(id) ON DELETE CASCADE,
    node_id    UUID NOT NULL REFERENCES nodes(id) ON DELETE CASCADE,
    bg_color   VARCHAR(20),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (graph_id, node_id)
);

CREATE INDEX IF NOT EXISTS idx_node_styles_graph ON node_styles(graph_id);
