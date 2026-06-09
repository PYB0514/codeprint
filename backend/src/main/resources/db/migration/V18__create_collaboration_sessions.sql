-- 실시간 협업 세션 및 참가자 테이블, 테스트 더미 유저

CREATE TABLE collaboration_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    graph_id UUID NOT NULL,
    owner_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    invite_code VARCHAR(8) UNIQUE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE session_participants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES collaboration_sessions(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (session_id, user_id)
);

CREATE INDEX idx_collaboration_sessions_graph_id ON collaboration_sessions(graph_id);
CREATE INDEX idx_collaboration_sessions_invite_code ON collaboration_sessions(invite_code);
CREATE INDEX idx_session_participants_session_id ON session_participants(session_id);

-- 로컬 테스트용 더미 유저 (github_id 9999999999, 실제 GitHub 계정 없음)
INSERT INTO users (id, github_id, email, username, plan, role, enabled, created_at, updated_at)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    9999999999,
    'testuser@codeprint.dev',
    '테스트유저',
    'FREE',
    'USER',
    true,
    now(),
    now()
);
