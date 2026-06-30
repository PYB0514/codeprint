// ParsedFileCachePostgresAdapter 테스트 — 내용해시 일치 필터링·빈 입력 단축·저장/정리 위임 (DB 없이 JPA mock)
package com.codeprint.infrastructure.persistence.analysis;

import com.codeprint.infrastructure.analysis.CachedParse;
import com.codeprint.infrastructure.analysis.ParsedFile;
import com.codeprint.infrastructure.analysis.ParsedFileJsonCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ParsedFileCachePostgresAdapterTest {

    @Mock
    private ParsedFileCacheJpaRepository jpa;

    private ParsedFileCachePostgresAdapter adapter;
    private final ParsedFileJsonCodec codec = new ParsedFileJsonCodec();
    private final UUID projectId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        adapter = new ParsedFileCachePostgresAdapter(jpa);
    }

    // 내용해시가 일치하는 행만 hit으로 반환하고, 같은 경로의 옛 해시 행은 제외한다
    @Test
    @DisplayName("내용해시가 일치하는 행만 hit으로 반환한다")
    void findAll_returnsOnlyHashMatchingRows() {
        ParsedFileCacheEntity match = row("a.java", "HASH_NEW", codec.encode(pf("a.java")));
        ParsedFileCacheEntity stale = row("a.java", "HASH_OLD", codec.encode(pf("a.java")));
        when(jpa.findByProjectIdAndAnalyzerVersionAndFilePathIn(eq(projectId), eq(1), anyCollection()))
                .thenReturn(List.of(match, stale));

        Map<String, ParsedFile> hits = adapter.findAll(projectId, 1, Map.of("a.java", "HASH_NEW"));

        assertThat(hits).containsOnlyKeys("a.java");
        assertThat(hits.get("a.java")).isEqualTo(pf("a.java"));
    }

    // 요청 경로 집합이 비면 DB를 치지 않고 빈 결과를 반환한다
    @Test
    @DisplayName("빈 입력은 DB 조회 없이 빈 결과")
    void findAll_emptyInput_shortCircuits() {
        Map<String, ParsedFile> hits = adapter.findAll(projectId, 1, Map.of());

        assertThat(hits).isEmpty();
        verifyNoInteractions(jpa);
    }

    // saveAll은 각 엔트리를 insertIgnore로 위임한다
    @Test
    @DisplayName("saveAll은 엔트리마다 insertIgnore를 호출한다")
    void saveAll_delegatesInsertIgnore() {
        adapter.saveAll(projectId, 1, List.of(new CachedParse("a.java", "HASH", pf("a.java"))));

        verify(jpa).insertIgnore(any(UUID.class), eq(projectId), eq("a.java"), eq("HASH"),
                eq(1), anyString(), any(Instant.class));
    }

    // evictOlderThan은 cutoff 삭제 쿼리로 위임한다
    @Test
    @DisplayName("evictOlderThan은 삭제 쿼리로 위임한다")
    void evict_delegatesDelete() {
        Instant cutoff = Instant.now();

        adapter.evictOlderThan(projectId, cutoff);

        verify(jpa).deleteByProjectIdAndUpdatedAtBefore(projectId, cutoff);
    }

    private ParsedFileCacheEntity row(String filePath, String contentHash, String parsedJson) {
        ParsedFileCacheEntity e = mock(ParsedFileCacheEntity.class);
        lenient().when(e.getFilePath()).thenReturn(filePath);
        lenient().when(e.getContentHash()).thenReturn(contentHash);
        lenient().when(e.getParsedJson()).thenReturn(parsedJson);
        return e;
    }

    private ParsedFile pf(String relPath) {
        return new ParsedFile(relPath, "Java", List.of("f"), List.of(), null, Map.of(), Map.of(),
                List.of(), List.of(), null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), Map.of(), List.of(), List.of(), List.of());
    }
}
