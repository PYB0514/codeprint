-- 파싱 결과 캐시 — 재분석 시 변경 안 된 파일의 ParsedFile을 재사용해 전체 재파싱을 회피
-- project_id는 FK 없이 opaque UUID로만 보유 — analysis 컨텍스트를 MSA로 떼낼 때 크로스 컨텍스트 결합이 없도록
create table parsed_file_cache (
    id               uuid          primary key,
    project_id       uuid          not null,
    file_path        varchar(1000) not null,
    content_hash     char(64)      not null,
    analyzer_version int           not null,
    parsed_json      text          not null,
    updated_at       timestamptz   not null default now()
);

-- 조회 키: (프로젝트, 경로, 내용해시, 분석기버전). 동시 삽입 충돌(on conflict do nothing) 대상이기도 함
create unique index ux_parsed_file_cache_key
    on parsed_file_cache (project_id, file_path, content_hash, analyzer_version);

-- 미사용 엔트리 정리(evictOlderThan) 가속
create index ix_parsed_file_cache_evict
    on parsed_file_cache (project_id, updated_at);
