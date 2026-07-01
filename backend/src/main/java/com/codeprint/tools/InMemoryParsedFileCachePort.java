// DB 없이 프로세스 메모리에만 유지하는 파싱 캐시 — LocalAnalyzer/LocalWatcher 공용
package com.codeprint.tools;

import com.codeprint.infrastructure.analysis.CachedParse;
import com.codeprint.infrastructure.analysis.ParsedFile;
import com.codeprint.infrastructure.analysis.ParsedFileCachePort;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryParsedFileCachePort implements ParsedFileCachePort {

    // key: projectId + relPath + contentHash
    private final Map<String, ParsedFile> cache = new ConcurrentHashMap<>();

    // 내용 해시가 일치하는 캐시만 반환 (hit)
    @Override
    public Map<String, ParsedFile> findAll(UUID projectId, int analyzerVersion, Map<String, String> pathToHash) {
        Map<String, ParsedFile> hits = new HashMap<>();
        pathToHash.forEach((relPath, hash) -> {
            ParsedFile hit = cache.get(key(projectId, analyzerVersion, relPath, hash));
            if (hit != null) hits.put(relPath, hit);
        });
        return hits;
    }

    // 새로 파싱된 결과를 메모리에 저장
    @Override
    public void saveAll(UUID projectId, int analyzerVersion, List<CachedParse> entries) {
        for (CachedParse entry : entries) {
            cache.put(key(projectId, analyzerVersion, entry.filePath(), entry.contentHash()), entry.parsedFile());
        }
    }

    // 프로세스 수명 동안만 유지되므로 별도 정리 불필요
    @Override
    public void evictOlderThan(UUID projectId, Instant cutoff) {
    }

    private String key(UUID projectId, int analyzerVersion, String relPath, String hash) {
        return projectId + ":" + analyzerVersion + ":" + relPath + ":" + hash;
    }
}
