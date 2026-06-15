-- 그래프 버전 고정 슬롯 — 보존 정책(최근 N개)에서 보호할 버전을 1~5번 슬롯에 고정
ALTER TABLE graphs ADD COLUMN pinned_slot SMALLINT;

-- 슬롯 번호는 1~5 또는 NULL(비고정)
ALTER TABLE graphs ADD CONSTRAINT chk_graphs_pinned_slot
    CHECK (pinned_slot IS NULL OR pinned_slot BETWEEN 1 AND 5);

-- 프로젝트당 같은 슬롯은 하나의 그래프만 점유 (NULL은 중복 허용)
CREATE UNIQUE INDEX uq_graphs_project_pinned_slot
    ON graphs (project_id, pinned_slot) WHERE pinned_slot IS NOT NULL;
