// 파싱 결과 캐시 행 — 쓰기는 네이티브 upsert로 처리하므로 이 엔티티는 읽기 매핑 전용
package com.codeprint.infrastructure.persistence.analysis;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "parsed_file_cache")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ParsedFileCacheEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "project_id", nullable = false, columnDefinition = "uuid")
    private UUID projectId;

    @Column(name = "file_path", nullable = false, length = 1000)
    private String filePath;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "analyzer_version", nullable = false)
    private int analyzerVersion;

    @Column(name = "parsed_json", nullable = false, columnDefinition = "text")
    private String parsedJson;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
