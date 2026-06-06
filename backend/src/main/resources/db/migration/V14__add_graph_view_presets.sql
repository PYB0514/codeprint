-- 그래프 뷰 프리셋 테이블 — 사용자별 슬롯 4개, config는 JSON 직렬화
CREATE TABLE graph_view_presets (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    graph_id    UUID NOT NULL REFERENCES graphs(id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    slot        INTEGER NOT NULL CHECK (slot BETWEEN 1 AND 4),
    name        VARCHAR(30) NOT NULL,
    config      JSONB NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (graph_id, user_id, slot)
);
