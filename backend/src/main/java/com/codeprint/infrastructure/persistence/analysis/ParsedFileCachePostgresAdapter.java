// 파싱 캐시 포트의 Postgres 구현 — ParsedFile↔JSON은 codec, 영속은 JPA. 내용해시 일치 판정은 메모리에서 수행
package com.codeprint.infrastructure.persistence.analysis;

import com.codeprint.infrastructure.analysis.CachedParse;
import com.codeprint.infrastructure.analysis.ParsedFile;
import com.codeprint.infrastructure.analysis.ParsedFileCachePort;
import com.codeprint.infrastructure.analysis.ParsedFileJsonCodec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ParsedFileCachePostgresAdapter implements ParsedFileCachePort {

    private final ParsedFileCacheJpaRepository jpa;
    private final ParsedFileJsonCodec codec = new ParsedFileJsonCodec();

    // 경로 집합으로 후보 행을 한 번에 가져온 뒤, 요청한 내용해시와 일치하는 것만 ParsedFile로 디코드해 반환
    @Override
    public Map<String, ParsedFile> findAll(UUID projectId, int analyzerVersion, Map<String, String> pathToHash) {
        if (pathToHash.isEmpty()) return Map.of();
        List<ParsedFileCacheEntity> rows =
                jpa.findByProjectIdAndAnalyzerVersionAndFilePathIn(projectId, analyzerVersion, pathToHash.keySet());
        Map<String, ParsedFile> hits = new HashMap<>();
        for (ParsedFileCacheEntity row : rows) {
            String wantHash = pathToHash.get(row.getFilePath());
            if (wantHash != null && wantHash.equals(row.getContentHash())) {
                hits.put(row.getFilePath(), codec.decode(row.getParsedJson()));
            }
        }
        return hits;
    }

    // 새 파싱 결과들을 멱등 삽입 (충돌 무시)
    @Override
    public void saveAll(UUID projectId, int analyzerVersion, List<CachedParse> entries) {
        Instant now = Instant.now();
        for (CachedParse entry : entries) {
            jpa.insertIgnore(UUID.randomUUID(), projectId, entry.filePath(), entry.contentHash(),
                    analyzerVersion, codec.encode(entry.parsedFile()), now);
        }
    }

    // cutoff 이전 미사용 엔트리 삭제
    @Override
    @Transactional
    public void evictOlderThan(UUID projectId, Instant cutoff) {
        jpa.deleteByProjectIdAndUpdatedAtBefore(projectId, cutoff);
    }
}
