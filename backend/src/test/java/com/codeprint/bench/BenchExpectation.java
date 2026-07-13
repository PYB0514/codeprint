// 벤치 케이스의 expected.json 오라클 — 실제 경고를 타입별 건수로 집계해 비교
package com.codeprint.bench;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public final class BenchExpectation {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private BenchExpectation() {
    }

    // expected.json 최상위 — warnings 배열만 사용(files는 향후 확장용, 지금은 무시)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Expected(List<Warning> warnings) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Warning(String type, int count) {}

    public static Expected load(Path caseDir) {
        try {
            return MAPPER.readValue(caseDir.resolve("expected.json").toFile(), Expected.class);
        } catch (IOException e) {
            throw new IllegalStateException("expected.json 로드 실패: " + caseDir, e);
        }
    }

    // 실제 경고를 타입별 건수로 집계해 expected.json과 정확히 일치하는지 검증(과다·과소 검출 둘 다 실패)
    public static void assertMatches(Path caseDir, List<Map<String, Object>> actual) {
        Expected expected = load(caseDir);
        Map<String, Long> actualCounts = actual.stream()
                .collect(Collectors.groupingBy(w -> String.valueOf(w.get("type")), Collectors.counting()));
        Map<String, Long> expectedCounts = expected.warnings().stream()
                .collect(Collectors.toMap(Warning::type, w -> (long) w.count()));
        assertThat(actualCounts)
                .as("벤치 케이스: %s", caseDir.getFileName())
                .isEqualTo(expectedCounts);
    }
}
