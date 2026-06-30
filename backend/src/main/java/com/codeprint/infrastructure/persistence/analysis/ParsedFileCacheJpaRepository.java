// 파싱 캐시 JPA 리포지터리 — 조회는 파생 쿼리, 쓰기는 on-conflict 네이티브 upsert
package com.codeprint.infrastructure.persistence.analysis;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ParsedFileCacheJpaRepository extends JpaRepository<ParsedFileCacheEntity, UUID> {

    // 프로젝트+분석기버전의 주어진 경로 집합에 대한 캐시 행 — 내용해시 일치 여부는 호출부(어댑터)가 메모리에서 판정
    List<ParsedFileCacheEntity> findByProjectIdAndAnalyzerVersionAndFilePathIn(
            UUID projectId, int analyzerVersion, Collection<String> filePaths);

    // 키 충돌 시 무시하는 삽입 — parallel 분석 동시 삽입에도 예외 없이(트랜잭션 오염 방지) 멱등 저장
    @Modifying
    @Query(value = """
            insert into parsed_file_cache
                (id, project_id, file_path, content_hash, analyzer_version, parsed_json, updated_at)
            values (:id, :projectId, :filePath, :contentHash, :analyzerVersion, :parsedJson, :updatedAt)
            on conflict (project_id, file_path, content_hash, analyzer_version) do nothing
            """, nativeQuery = true)
    void insertIgnore(@Param("id") UUID id, @Param("projectId") UUID projectId,
                      @Param("filePath") String filePath, @Param("contentHash") String contentHash,
                      @Param("analyzerVersion") int analyzerVersion, @Param("parsedJson") String parsedJson,
                      @Param("updatedAt") Instant updatedAt);

    // cutoff 이전 미사용 엔트리 삭제
    @Modifying
    @Query("delete from ParsedFileCacheEntity e where e.projectId = :projectId and e.updatedAt < :cutoff")
    void deleteByProjectIdAndUpdatedAtBefore(@Param("projectId") UUID projectId, @Param("cutoff") Instant cutoff);
}
