-- 노드별 커스텀 스타일 저장 테이블 — 배경색 등 사용자 정의 시각화 속성
-- 구버전 node_styles 테이블(구 스키마) 제거 후 신규 생성
DROP TABLE IF EXISTS node_styles;
CREATE TABLE node_styles (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    graph_id   UUID NOT NULL REFERENCES graphs(id) ON DELETE CASCADE,
    node_id    UUID NOT NULL REFERENCES nodes(id) ON DELETE CASCADE,
    bg_color   VARCHAR(20),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (graph_id, node_id)
);

CREATE INDEX idx_node_styles_graph ON node_styles(graph_id);
