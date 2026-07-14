-- 팀 단위 API 키 — 비공개 프로젝트 교차 조회(에이전트 인증) 용도, 평문 키는 저장하지 않고 해시만 보관

CREATE TABLE team_api_keys (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id        UUID         NOT NULL,
    name           VARCHAR(100) NOT NULL,
    key_hash       VARCHAR(64)  NOT NULL,
    key_prefix     VARCHAR(12)  NOT NULL,
    created_by     UUID         NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_used_at   TIMESTAMPTZ,
    revoked_at     TIMESTAMPTZ,
    CONSTRAINT fk_tak_team    FOREIGN KEY (team_id)    REFERENCES teams(id) ON DELETE CASCADE,
    CONSTRAINT fk_tak_creator FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uq_team_api_key_hash UNIQUE (key_hash)
);

CREATE INDEX idx_team_api_keys_team ON team_api_keys(team_id);
