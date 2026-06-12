// SourceFileWalker 수집 대상 회귀 테스트 — schema.prisma 미수집 데드 코드 재발 방지
package com.codeprint.infrastructure.analysis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SourceFileWalkerTest {

    private SourceFileWalker walker;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        walker = new SourceFileWalker();
    }

    @Test
    @DisplayName("schema.prisma 파일이 분석 대상으로 수집된다")
    void prisma_파일_수집() throws IOException {
        // 회귀: LanguageDetector에 .prisma 확장자가 없어 StaticCodeAnalyzer의 Prisma 분기가 도달 불가능한 데드 코드였던 버그
        Files.createDirectories(tempDir.resolve("prisma"));
        Files.writeString(tempDir.resolve("prisma/schema.prisma"), """
                model User {
                  id   Int    @id @default(autoincrement())
                  name String
                }
                """);
        Files.writeString(tempDir.resolve("app.ts"), "export const x = 1;");

        List<Path> files = walker.walk(tempDir);

        assertThat(files).anyMatch(p -> p.getFileName().toString().equals("schema.prisma"));
        assertThat(files).anyMatch(p -> p.getFileName().toString().equals("app.ts"));
    }

    @Test
    @DisplayName("지원하지 않는 확장자(md 등)는 수집되지 않는다")
    void 미지원_확장자_미수집() throws IOException {
        Files.writeString(tempDir.resolve("README.md"), "# readme");
        Files.writeString(tempDir.resolve("User.java"), "public class User {}");

        List<Path> files = walker.walk(tempDir);

        assertThat(files).hasSize(1);
        assertThat(files.get(0).getFileName().toString()).isEqualTo("User.java");
    }

    @Test
    @DisplayName("node_modules 안의 파일은 수집되지 않는다")
    void 스킵_디렉토리_미수집() throws IOException {
        Files.createDirectories(tempDir.resolve("node_modules/pkg"));
        Files.writeString(tempDir.resolve("node_modules/pkg/index.js"), "module.exports = {};");
        Files.writeString(tempDir.resolve("index.js"), "const a = 1;");

        List<Path> files = walker.walk(tempDir);

        assertThat(files).hasSize(1);
        assertThat(files.get(0).getFileName().toString()).isEqualTo("index.js");
    }
}
