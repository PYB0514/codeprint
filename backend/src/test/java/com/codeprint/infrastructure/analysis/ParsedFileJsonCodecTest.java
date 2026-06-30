// ParsedFileJsonCodec round-trip 동치 테스트 — 캐시 재사용이 파싱 결과와 동일함을 보장(결정론)
package com.codeprint.infrastructure.analysis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ParsedFileJsonCodecTest {

    private final ParsedFileJsonCodec codec = new ParsedFileJsonCodec();

    // 모든 필드를 채운 ParsedFile이 직렬화→역직렬화 후 원본과 완전히 동일한지
    @Test
    @DisplayName("모든 필드를 채운 ParsedFile은 round-trip 후 원본과 동일하다")
    void fullyPopulated_roundTrips_equal() {
        ParsedFile original = fullFixture();

        ParsedFile restored = codec.decode(codec.encode(original));

        assertThat(restored).isEqualTo(original);
    }

    // null·빈 컬렉션이 섞인 최소 ParsedFile도 round-trip 후 동일한지
    @Test
    @DisplayName("null·빈 컬렉션이 섞인 ParsedFile도 round-trip 후 동일하다")
    void withNullsAndEmpties_roundTrips_equal() {
        ParsedFile original = new ParsedFile(
                "src/Empty.java", "java",
                List.of(), List.of(),
                null,                       // fileComment
                Map.of(), Map.of(),
                List.of(), List.of(),
                null,                       // repositoryEntityClass
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), Map.of(), List.of(), List.of(), List.of()
        );

        ParsedFile restored = codec.decode(codec.encode(original));

        assertThat(restored).isEqualTo(original);
    }

    // functionDefCounts의 숫자가 Integer 타입으로 보존되는지 (Long으로 넓어지면 equals 깨짐)
    @Test
    @DisplayName("functionDefCounts 값이 Integer 타입으로 보존된다")
    void functionDefCounts_preservesIntegerType() {
        ParsedFile restored = codec.decode(codec.encode(fullFixture()));

        assertThat(restored.functionDefCounts()).containsEntry("merged", 2);
        assertThat(restored.functionDefCounts().get("merged")).isInstanceOf(Integer.class);
    }

    // 모든 필드(중첩 record·Map·List 포함)를 비자명한 값으로 채운 픽스처
    private ParsedFile fullFixture() {
        Map<String, List<String>> calls = new LinkedHashMap<>();
        calls.put("handle", List.of("Service::save", "validate"));

        Map<String, Integer> defCounts = new LinkedHashMap<>();
        defCounts.put("merged", 2);

        return new ParsedFile(
                "src/main/java/com/example/UserController.java", "java",
                List.of("handle", "validate"),
                List.of("com.example.UserService"),
                "사용자 요청을 처리하는 컨트롤러",
                Map.of("handle", "요청 처리"),
                calls,
                List.of("UserService"),
                List.of(new DbTableInfo("users", "User")),
                "User",
                List.of(new ColumnInfo("name", "user_name", "String", true)),
                List.of("GET:/api/users/{id}"),
                List.of("/api/users/{id}"),
                List.of("UserPort"),
                List.of("handleAsync"),
                List.of("UserCard"),
                List.of(new RawSqlAccess("audit_log", true)),
                List.of("handle"),
                List.of("validate"),
                defCounts,
                List.of("UserController"),
                List.of("shouldHandle"),
                List.of(new DbAccess("Session", false))
        );
    }
}
