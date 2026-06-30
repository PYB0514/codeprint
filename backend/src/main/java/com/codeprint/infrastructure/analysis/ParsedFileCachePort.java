// 파싱 결과 캐시 포트 — 서버(Postgres)/데스크탑(로컬) 구현을 갈아끼우기 위한 경계
package com.codeprint.infrastructure.analysis;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 배치형 계약 — 분석은 항상 레포 전체를 한 번에 처리하므로, DB 접근을 파일당이 아니라 조회/저장 각 1회로 모은다.
public interface ParsedFileCachePort {

    // 여러 파일을 한 번에 조회 — pathToHash(상대경로→내용해시)와 일치하는 캐시만 반환(상대경로→ParsedFile). hit만 포함.
    Map<String, ParsedFile> findAll(UUID projectId, int analyzerVersion, Map<String, String> pathToHash);

    // 새로 파싱된 결과들을 일괄 저장 (동일 키 동시 삽입은 무시)
    void saveAll(UUID projectId, int analyzerVersion, List<CachedParse> entries);

    // 프로젝트의 cutoff 이전(미사용) 캐시 엔트리 삭제 — 무한 증가 방지
    void evictOlderThan(UUID projectId, Instant cutoff);
}
