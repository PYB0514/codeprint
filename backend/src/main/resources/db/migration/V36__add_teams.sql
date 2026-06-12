-- 팀 플랜(Seat Pool) 관련 테이블 3종 추가

-- teams: 팀 단위 플랜 보유 정보
CREATE TABLE teams (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_user_id   UUID NOT NULL,
    name            VARCHAR(100) NOT NULL,
    plan            VARCHAR(30)  NOT NULL DEFAULT 'TEAM_STARTER',
    total_seats     INT          NOT NULL DEFAULT 15,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_team_owner FOREIGN KEY (owner_user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- team_members: 팀 소속 유저 (OWNER / MEMBER)
CREATE TABLE team_members (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id     UUID        NOT NULL,
    user_id     UUID        NOT NULL,
    role        VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    joined_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_tm_team FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE,
    CONSTRAINT fk_tm_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uq_team_member UNIQUE (team_id, user_id)
);

-- team_project_allocations: 프로젝트별 배분된 석수
CREATE TABLE team_project_allocations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id         UUID NOT NULL,
    project_id      UUID NOT NULL,
    allocated_seats INT  NOT NULL DEFAULT 0,
    CONSTRAINT fk_tpa_team    FOREIGN KEY (team_id)    REFERENCES teams(id)    ON DELETE CASCADE,
    CONSTRAINT fk_tpa_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    CONSTRAINT uq_team_project UNIQUE (team_id, project_id),
    CONSTRAINT chk_seats_positive CHECK (allocated_seats >= 0)
);

CREATE INDEX idx_team_members_team ON team_members(team_id);
CREATE INDEX idx_team_members_user ON team_members(user_id);
CREATE INDEX idx_tpa_team ON team_project_allocations(team_id);
