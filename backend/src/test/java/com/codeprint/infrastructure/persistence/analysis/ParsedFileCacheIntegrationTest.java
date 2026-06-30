// 파싱 캐시 통합 테스트 — 실 Postgres(localhost:5432)로 Flyway 전체 마이그레이션 + Hibernate validate(스키마 가드) + 어댑터 영속 동작 검증
// 로컬은 docker compose의 codeprint-db, CI는 ci.yml의 postgres 서비스에 접속(둘 다 postgres/1234/codeprint).
package com.codeprint.infrastructure.persistence.analysis;

import com.codeprint.infrastructure.analysis.CachedParse;
import com.codeprint.infrastructure.analysis.ParsedFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// ddl-auto=validate를 강제해, Flyway가 만든 스키마와 모든 @Entity 매핑이 어긋나면 컨텍스트 로드 자체가 실패한다
// → 이 테스트가 뜨는 것만으로 char/varchar·SMALLINT/Integer 류(ERROR_TRACKER [반복-F]) 스키마 불일치가 차단된다.
// 트랜잭션 롤백(@DataJpaTest 기본)이라 실 DB에 잔여 데이터를 남기지 않는다.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ParsedFileCachePostgresAdapter.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/codeprint",
        "spring.datasource.username=postgres",
        "spring.datasource.password=1234",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        // 앱(application-local.yml)과 동일 — 기존 dev DB의 V30 체크섬 드리프트(과거 적용 후 편집, 본 작업과 무관) 무시.
        // 핵심 가드인 Hibernate ddl-auto=validate는 유지되므로 스키마 불일치(char/varchar 류)는 계속 검출된다.
        "spring.flyway.validate-on-migrate=false"
})
class ParsedFileCacheIntegrationTest {

    @Autowired
    private ParsedFileCachePostgresAdapter adapter;

    // 충돌 방지용 랜덤 프로젝트 — 실 DB의 기존 데이터와 격리
    private final UUID projectId = UUID.randomUUID();

    // 저장한 ParsedFile이 실 DB(text 컬럼 codec 왕복)를 거쳐 동일하게 조회된다
    @Test
    @DisplayName("저장 후 동일 키로 조회하면 원본 ParsedFile과 동일하다")
    void savesAndFindsByKey() {
        adapter.saveAll(projectId, 1, List.of(new CachedParse("a.java", "HASH1", pf("a.java"))));

        Map<String, ParsedFile> hits = adapter.findAll(projectId, 1, Map.of("a.java", "HASH1"));

        assertThat(hits).containsOnlyKeys("a.java");
        assertThat(hits.get("a.java")).isEqualTo(pf("a.java"));
    }

    // 내용 해시가 다르면 miss (변경 파일 = 재파싱 대상)
    @Test
    @DisplayName("내용 해시가 다르면 miss")
    void missOnContentHashChange() {
        adapter.saveAll(projectId, 1, List.of(new CachedParse("a.java", "HASH1", pf("a.java"))));

        assertThat(adapter.findAll(projectId, 1, Map.of("a.java", "HASH2"))).isEmpty();
    }

    // analyzer_version이 다르면 miss (엔진 변경 시 전체 무효화)
    @Test
    @DisplayName("analyzer_version이 다르면 miss")
    void missOnAnalyzerVersionChange() {
        adapter.saveAll(projectId, 1, List.of(new CachedParse("a.java", "HASH1", pf("a.java"))));

        assertThat(adapter.findAll(projectId, 2, Map.of("a.java", "HASH1"))).isEmpty();
    }

    // cutoff 이전 엔트리는 evict로 삭제된다
    @Test
    @DisplayName("evictOlderThan은 cutoff 이전 엔트리를 삭제한다")
    void evictsOlderThanCutoff() {
        adapter.saveAll(projectId, 1, List.of(new CachedParse("a.java", "HASH1", pf("a.java"))));

        adapter.evictOlderThan(projectId, Instant.now().plus(Duration.ofDays(1)));

        assertThat(adapter.findAll(projectId, 1, Map.of("a.java", "HASH1"))).isEmpty();
    }

    // 동일 키 재저장은 on-conflict로 무시 — 예외 없이 1건 유지 (parallel 분석 동시 삽입 대비)
    @Test
    @DisplayName("동일 키 재저장은 예외 없이 무시된다(멱등)")
    void insertIgnoreIsIdempotent() {
        CachedParse entry = new CachedParse("a.java", "HASH1", pf("a.java"));
        adapter.saveAll(projectId, 1, List.of(entry));
        adapter.saveAll(projectId, 1, List.of(entry));

        assertThat(adapter.findAll(projectId, 1, Map.of("a.java", "HASH1"))).containsOnlyKeys("a.java");
    }

    private ParsedFile pf(String relPath) {
        return new ParsedFile(relPath, "Java", List.of("f"), List.of(), null, Map.of(), Map.of(),
                List.of(), List.of(), null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), Map.of(), List.of(), List.of(), List.of());
    }
}
