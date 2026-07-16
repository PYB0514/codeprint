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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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

        List<Path> files = walker.walk(tempDir).files();

        assertThat(files).anyMatch(p -> p.getFileName().toString().equals("schema.prisma"));
        assertThat(files).anyMatch(p -> p.getFileName().toString().equals("app.ts"));
    }

    @Test
    @DisplayName("지원하지 않는 확장자(md 등)는 수집되지 않는다")
    void 미지원_확장자_미수집() throws IOException {
        Files.writeString(tempDir.resolve("README.md"), "# readme");
        Files.writeString(tempDir.resolve("User.java"), "public class User {}");

        List<Path> files = walker.walk(tempDir).files();

        assertThat(files).hasSize(1);
        assertThat(files.get(0).getFileName().toString()).isEqualTo("User.java");
    }

    @Test
    @DisplayName("node_modules 안의 파일은 수집되지 않는다")
    void 스킵_디렉토리_미수집() throws IOException {
        Files.createDirectories(tempDir.resolve("node_modules/pkg"));
        Files.writeString(tempDir.resolve("node_modules/pkg/index.js"), "module.exports = {};");
        Files.writeString(tempDir.resolve("index.js"), "const a = 1;");

        List<Path> files = walker.walk(tempDir).files();

        assertThat(files).hasSize(1);
        assertThat(files.get(0).getFileName().toString()).isEqualTo("index.js");
    }

    @Test
    @DisplayName("중첩·복수 스킵 디렉터리(.git/node_modules 깊은 경로)는 순회하지 않고 제외된다")
    void 중첩_스킵_디렉토리_가지치기() throws IOException {
        Files.createDirectories(tempDir.resolve("node_modules/a/b/c"));
        Files.writeString(tempDir.resolve("node_modules/a/b/c/deep.js"), "const x = 1;");
        Files.createDirectories(tempDir.resolve(".git/objects"));
        Files.writeString(tempDir.resolve(".git/objects/pack.go"), "package p");
        Files.writeString(tempDir.resolve("main.go"), "package main");

        List<Path> files = walker.walk(tempDir).files();

        assertThat(files).extracting(p -> p.getFileName().toString())
                .containsExactly("main.go");
    }

    @Test
    @DisplayName("읽을 수 없는 끊긴 심링크가 있어도 walk가 실패하지 않고 정상 파일은 수집된다")
    void 끊긴_심링크_내성() throws IOException {
        Files.writeString(tempDir.resolve("App.java"), "public class App {}");
        // 끊긴 심링크 생성 — 권한 부족(Windows 등)으로 실패하면 이 테스트는 건너뛴다
        boolean linkCreated;
        try {
            Files.createSymbolicLink(tempDir.resolve("Dangling.java"), tempDir.resolve("no_such_target.java"));
            linkCreated = true;
        } catch (IOException | UnsupportedOperationException e) {
            linkCreated = false;
        }
        assumeTrue(linkCreated, "심링크 생성 불가 환경 — 건너뜀");

        assertThatCode(() -> {
            List<Path> files = walker.walk(tempDir).files();
            assertThat(files).extracting(p -> p.getFileName().toString()).contains("App.java");
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("500개 초과 시 절단되고 전체 대상 수가 함께 반환된다")
    void 최대_파일_수_절단_감지() throws IOException {
        for (int i = 0; i < 502; i++) {
            Files.writeString(tempDir.resolve("File" + i + ".java"), "public class File" + i + " {}");
        }

        WalkResult result = walker.walk(tempDir);

        assertThat(result.files()).hasSize(500);
        assertThat(result.totalEligible()).isEqualTo(502);
    }

    @Test
    @DisplayName("500개 이하면 전체 대상 수와 수집 수가 같다")
    void 절단_없음_카운트_일치() throws IOException {
        Files.writeString(tempDir.resolve("A.java"), "public class A {}");
        Files.writeString(tempDir.resolve("B.java"), "public class B {}");

        WalkResult result = walker.walk(tempDir);

        assertThat(result.files()).hasSize(2);
        assertThat(result.totalEligible()).isEqualTo(2);
    }

    @Test
    @DisplayName("절단 시 파일 경로 정렬 순으로 앞 500개가 선택된다 — 파일시스템 순회 순서에 의존하지 않는 결정론 보장")
    void 절단_결정론_정렬순() throws IOException {
        // 역순으로 생성해도(생성 순서가 곧 순회 순서가 되기 쉬운 일부 파일시스템 대비) 결과는 항상 경로 정렬 순이어야 한다
        for (int i = 501; i >= 0; i--) {
            Files.writeString(tempDir.resolve(String.format("File%03d.java", i)), "public class C {}");
        }

        WalkResult result = walker.walk(tempDir);

        List<String> names = result.files().stream().map(p -> p.getFileName().toString()).toList();
        assertThat(names).hasSize(500);
        assertThat(names).isSorted();
        assertThat(names.get(0)).isEqualTo("File000.java");
        assertThat(names.get(499)).isEqualTo("File499.java");
    }
}
