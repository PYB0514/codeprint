// ParsedFile을 파싱 캐시 저장용 JSON으로 직렬화/역직렬화 (결정론 보존 — round-trip 동치가 그래프 동일성의 전제)
package com.codeprint.infrastructure.analysis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

// 캐시는 파싱이 만들었을 ParsedFile과 동일한 값을 돌려줘야 그래프 출력이 비트 단위로 같아진다.
// 그래서 이 코덱의 유일한 계약은 decode(encode(x)).equals(x) — round-trip 동치다.
public class ParsedFileJsonCodec {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ParsedFile을 JSON 문자열로 직렬화
    public String encode(ParsedFile parsedFile) {
        try {
            return objectMapper.writeValueAsString(parsedFile);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("ParsedFile 직렬화 실패: " + parsedFile.filePath(), e);
        }
    }

    // JSON 문자열을 ParsedFile로 역직렬화
    public ParsedFile decode(String json) {
        try {
            return objectMapper.readValue(json, ParsedFile.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("ParsedFile 역직렬화 실패", e);
        }
    }
}
