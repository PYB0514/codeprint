-- Users
CREATE TABLE IF NOT EXISTS users (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    github_id   BIGINT UNIQUE NOT NULL,
    email       VARCHAR(255) UNIQUE NOT NULL,
    username    VARCHAR(100) NOT NULL,
    plan        VARCHAR(20) NOT NULL DEFAULT 'FREE',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Projects
CREATE TABLE IF NOT EXISTS projects (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    github_repo_url VARCHAR(500) NOT NULL,
    name            VARCHAR(200) NOT NULL,
    description     TEXT,
    is_public       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Analyses
CREATE TABLE IF NOT EXISTS analyses (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id  UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    status      VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    progress    INT NOT NULL DEFAULT 0,
    error_msg   TEXT,
    started_at  TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Graphs
CREATE TABLE IF NOT EXISTS graphs (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id  UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    analysis_id UUID NOT NULL REFERENCES analyses(id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Nodes
CREATE TABLE IF NOT EXISTS nodes (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    graph_id    UUID NOT NULL REFERENCES graphs(id) ON DELETE CASCADE,
    type        VARCHAR(30) NOT NULL,
    name        VARCHAR(500) NOT NULL,
    file_path   VARCHAR(1000),
    language    VARCHAR(50),
    metadata    JSONB,
    pos_x       FLOAT NOT NULL DEFAULT 0,
    pos_y       FLOAT NOT NULL DEFAULT 0,
    is_hidden   BOOLEAN NOT NULL DEFAULT FALSE
);

-- Edges
CREATE TABLE IF NOT EXISTS edges (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    graph_id        UUID NOT NULL REFERENCES graphs(id) ON DELETE CASCADE,
    edge_identifier VARCHAR(500) NOT NULL,
    type            VARCHAR(30) NOT NULL,
    source_node_id  UUID NOT NULL REFERENCES nodes(id) ON DELETE CASCADE,
    target_node_id  UUID NOT NULL REFERENCES nodes(id) ON DELETE CASCADE,
    metadata        JSONB,
    is_hidden       BOOLEAN NOT NULL DEFAULT FALSE
);

-- Node styles (3차)
CREATE TABLE IF NOT EXISTS node_styles (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    node_id   UUID NOT NULL REFERENCES nodes(id) ON DELETE CASCADE,
    color     VARCHAR(20),
    font_size INT,
    icon      VARCHAR(100),
    group_id  UUID
);

-- Edge styles (3차)
CREATE TABLE IF NOT EXISTS edge_styles (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    edge_id     UUID NOT NULL REFERENCES edges(id) ON DELETE CASCADE,
    color       VARCHAR(20),
    line_style  VARCHAR(20),
    thickness   INT
);

-- Posts
CREATE TABLE IF NOT EXISTS posts (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    graph_id      UUID REFERENCES graphs(id) ON DELETE SET NULL,
    title         VARCHAR(300) NOT NULL,
    content       TEXT,
    feedback_type VARCHAR(50),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Comments
CREATE TABLE IF NOT EXISTS comments (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id    UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content    TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_projects_user_id    ON projects(user_id);
CREATE INDEX IF NOT EXISTS idx_analyses_project_id ON analyses(project_id);
CREATE INDEX IF NOT EXISTS idx_nodes_graph_id      ON nodes(graph_id);
CREATE INDEX IF NOT EXISTS idx_edges_graph_id      ON edges(graph_id);
CREATE INDEX IF NOT EXISTS idx_edges_source        ON edges(source_node_id);
CREATE INDEX IF NOT EXISTS idx_edges_target        ON edges(target_node_id);
CREATE INDEX IF NOT EXISTS idx_posts_user_id       ON posts(user_id);
CREATE INDEX IF NOT EXISTS idx_comments_post_id    ON comments(post_id);
