-- 오늘의 공개레포 큐레이션 후보 풀 + 노출용 시스템 계정 시딩

INSERT INTO users (id, github_id, email, username, plan, role, enabled, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000000', -1, 'featured-repos@codeprint.internal', 'Codeprint 공식',
        'FREE', 'USER', true, NOW(), NOW());

CREATE TABLE featured_repos (
    id               UUID         PRIMARY KEY,
    repo_full_name   VARCHAR(200) NOT NULL UNIQUE,
    language         VARCHAR(50)  NOT NULL,
    project_id       UUID,
    stars            INT,
    description      TEXT,
    last_featured_at TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

INSERT INTO featured_repos (id, repo_full_name, language, created_at) VALUES
    (gen_random_uuid(), 'spring-projects/spring-petclinic', 'Java', NOW()),
    (gen_random_uuid(), 'pallets/flask', 'Python', NOW()),
    (gen_random_uuid(), 'psf/requests', 'Python', NOW()),
    (gen_random_uuid(), 'expressjs/express', 'TypeScript', NOW()),
    (gen_random_uuid(), 'facebook/react', 'TypeScript', NOW()),
    (gen_random_uuid(), 'gin-gonic/gin', 'Go', NOW()),
    (gen_random_uuid(), 'BurntSushi/ripgrep', 'Rust', NOW()),
    (gen_random_uuid(), 'tokio-rs/mini-redis', 'Rust', NOW()),
    (gen_random_uuid(), 'JamesNK/Newtonsoft.Json', 'C#', NOW()),
    (gen_random_uuid(), 'sinatra/sinatra', 'Ruby', NOW()),
    (gen_random_uuid(), 'laravel/laravel', 'PHP', NOW()),
    (gen_random_uuid(), 'Alamofire/Alamofire', 'Swift', NOW()),
    (gen_random_uuid(), 'curl/curl', 'C', NOW()),
    (gen_random_uuid(), 'nlohmann/json', 'C++', NOW()),
    (gen_random_uuid(), 'redis/redis', 'C', NOW());
