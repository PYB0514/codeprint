// UnifiedDiffUtil 단위 테스트 — 컨텍스트/추가/삭제 줄 적용, 컨텍스트 불일치 시 예외
package com.codeprint.application.graph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UnifiedDiffUtilTest {

    private static final String ORIGINAL = String.join("\n",
            "package infrastructure.persistence;",
            "",
            "public class PostBookmarkJpaRepository {",
            "    public void deleteByUserIdAndPostId(java.util.UUID userId, java.util.UUID postId) {",
            "    }",
            "}") + "\n";

    @Test
    @DisplayName("import + 애노테이션 추가 diff를 원문에 정확히 적용")
    void appliesAdditions() {
        String diff = String.join("\n",
                "--- a/infrastructure/persistence/PostBookmarkJpaRepository.java",
                "+++ b/infrastructure/persistence/PostBookmarkJpaRepository.java",
                "@@ -1,6 +1,9 @@",
                " package infrastructure.persistence;",
                " ",
                "+import org.springframework.transaction.annotation.Transactional;",
                "+",
                " public class PostBookmarkJpaRepository {",
                "+    @Transactional",
                "     public void deleteByUserIdAndPostId(java.util.UUID userId, java.util.UUID postId) {",
                "     }",
                " }");

        String patched = UnifiedDiffUtil.apply(ORIGINAL, diff);

        assertThat(patched).isEqualTo(String.join("\n",
                "package infrastructure.persistence;",
                "",
                "import org.springframework.transaction.annotation.Transactional;",
                "",
                "public class PostBookmarkJpaRepository {",
                "    @Transactional",
                "    public void deleteByUserIdAndPostId(java.util.UUID userId, java.util.UUID postId) {",
                "    }",
                "}") + "\n");
    }

    @Test
    @DisplayName("삭제 줄이 있는 diff도 적용")
    void appliesDeletions() {
        String original = String.join("\n", "a", "b", "c") + "\n";
        String diff = String.join("\n",
                "@@ -1,3 +1,2 @@",
                " a",
                "-b",
                " c");

        String patched = UnifiedDiffUtil.apply(original, diff);

        assertThat(patched).isEqualTo(String.join("\n", "a", "c") + "\n");
    }

    @Test
    @DisplayName("컨텍스트 줄이 원문과 다르면 예외 — 패치 적용 실패로 노출")
    void mismatchedContext_throws() {
        String diff = String.join("\n",
                "@@ -1,6 +1,7 @@",
                " package infrastructure.persistence;",
                " ",
                "+import x;",
                " public class 다른클래스명 {",
                "     public void deleteByUserIdAndPostId(java.util.UUID userId, java.util.UUID postId) {",
                "     }",
                " }");

        assertThatThrownBy(() -> UnifiedDiffUtil.apply(ORIGINAL, diff))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("컨텍스트 불일치");
    }

    @Test
    @DisplayName("헝크 중간에 마커(공백/+/-) 없는 줄이 있으면 조용히 건너뛰지 않고 예외 — 삭제/컨텍스트 유실 방지")
    void unmarkedLineMidHunk_throwsInsteadOfSilentlySkipping() {
        String original = String.join("\n", "a", "b", "c", "d") + "\n";
        // "b" 앞에 컨텍스트 마커 공백이 빠짐(LLM diff에서 흔한 실수) — 예전엔 이 줄과 이후 " d"까지 조용히
        // 스킵된 채 apply()가 예외 없이 "성공"을 반환해, 트윈 검증이 실제로 반영 안 된 패치를 통과시켰다.
        String diff = String.join("\n", "@@ -1,4 +1,5 @@", " a", "+X", "b", " d");

        assertThatThrownBy(() -> UnifiedDiffUtil.apply(original, diff))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("알 수 없는 마커");
    }

    @Test
    @DisplayName("빈 줄은 마커가 트리밍된 blank 컨텍스트로 취급 — 원문과 실제로 대조된다")
    void emptyLineInHunk_treatedAsBlankContext() {
        String original = String.join("\n", "a", "", "c") + "\n";
        String diff = String.join("\n", "@@ -1,3 +1,4 @@", " a", "", "+X", " c");

        String patched = UnifiedDiffUtil.apply(original, diff);

        assertThat(patched).isEqualTo(String.join("\n", "a", "", "X", "c") + "\n");
    }

    @Test
    @DisplayName("헝크가 없는 diff는 예외")
    void noHunk_throws() {
        assertThatThrownBy(() -> UnifiedDiffUtil.apply(ORIGINAL, "설명만 있고 diff는 없음"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("헝크");
    }
}
