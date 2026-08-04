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
    @DisplayName("헝크가 없는 diff는 예외")
    void noHunk_throws() {
        assertThatThrownBy(() -> UnifiedDiffUtil.apply(ORIGINAL, "설명만 있고 diff는 없음"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("헝크");
    }
}
