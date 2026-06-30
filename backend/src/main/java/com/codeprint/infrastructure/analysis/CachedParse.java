// 캐시에 저장할 단일 파싱 결과 — 상대경로 + 내용해시 + ParsedFile
package com.codeprint.infrastructure.analysis;

public record CachedParse(String filePath, String contentHash, ParsedFile parsedFile) {}
