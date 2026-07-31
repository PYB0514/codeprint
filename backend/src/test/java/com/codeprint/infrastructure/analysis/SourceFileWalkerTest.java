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
import java.util.Set;

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

    @Test
    @DisplayName("절단 시 테스트 경로가 알파벳순으로 앞서도 프로덕션 소스가 먼저 채워지고 테스트는 남는 슬롯만 차지한다 — G-9 사전순 편향 완화")
    void 절단_시_프로덕션_소스_우선_테스트_후순위() throws IOException {
        // src/test 하위는 알파벳상 "a"로 시작해 정렬상 항상 앞서지만, 프로덕션(z로 시작)을 밀어내면 안 된다
        Files.createDirectories(tempDir.resolve("src/test"));
        for (int i = 0; i < 500; i++) {
            Files.writeString(tempDir.resolve(String.format("src/test/AFile%03d.java", i)), "class T {}");
        }
        Files.writeString(tempDir.resolve("ZProd1.java"), "class P1 {}");
        Files.writeString(tempDir.resolve("ZProd2.java"), "class P2 {}");

        WalkResult result = walker.walk(tempDir);

        List<String> names = result.files().stream().map(p -> p.getFileName().toString()).toList();
        assertThat(result.totalEligible()).isEqualTo(502);
        assertThat(names).hasSize(500);
        assertThat(names).contains("ZProd1.java", "ZProd2.java");
        assertThat(names).filteredOn(n -> n.startsWith("AFile")).hasSize(498);
    }

    @Test
    @DisplayName("절단 시 사전순으로 밀리는 작은 서브트리도 라운드로빈으로 전량 포함된다 — G-9 T1")
    void 서브트리_라운드로빈_전멸_방지() throws IOException {
        // adir(600개)이 알파벳순으로 bdir보다 앞서 순수 정렬이면 bdir을 통째로 밀어내지만,
        // 라운드로빈은 파일 수가 적은 bdir을 전량 포함하고 남는 슬롯만 adir에 배정해야 한다
        Files.createDirectories(tempDir.resolve("adir"));
        Files.createDirectories(tempDir.resolve("bdir"));
        for (int i = 0; i < 600; i++) {
            Files.writeString(tempDir.resolve(String.format("adir/File%03d.java", i)), "class A {}");
        }
        for (int i = 0; i < 100; i++) {
            Files.writeString(tempDir.resolve(String.format("bdir/File%03d.java", i)), "class B {}");
        }

        WalkResult result = walker.walk(tempDir);

        List<String> rels = result.files().stream()
                .map(p -> tempDir.relativize(p).toString().replace('\\', '/'))
                .toList();
        assertThat(rels).hasSize(500);
        long bdirCount = rels.stream().filter(r -> r.startsWith("bdir/")).count();
        long adirCount = rels.stream().filter(r -> r.startsWith("adir/")).count();
        assertThat(bdirCount).isEqualTo(100);
        assertThat(adirCount).isEqualTo(400);
    }

    @Test
    @DisplayName("PR diff 우선순위 파일은 라운드로빈에서 밀려도 항상 포함된다 — G-9 T0")
    void 우선순위_파일_항상_포함() throws IOException {
        // adir·bdir 각 500개씩 두면 라운드로빈으로 각 250개(File000~249)만 선택되고 File499는 정상적으론 제외된다
        Files.createDirectories(tempDir.resolve("adir"));
        Files.createDirectories(tempDir.resolve("bdir"));
        for (int i = 0; i < 500; i++) {
            Files.writeString(tempDir.resolve(String.format("adir/File%03d.java", i)), "class A {}");
            Files.writeString(tempDir.resolve(String.format("bdir/File%03d.java", i)), "class B {}");
        }

        WalkResult result = walker.walk(tempDir, Set.of("adir/File499.java"));

        List<String> rels = result.files().stream()
                .map(p -> tempDir.relativize(p).toString().replace('\\', '/'))
                .toList();
        assertThat(rels).hasSize(500);
        assertThat(rels).contains("adir/File499.java");
    }

    @Test
    @DisplayName("미니파이 파일(*.min.*)은 수집되지 않는다")
    void 미니파이_파일_미수집() throws IOException {
        Files.writeString(tempDir.resolve("jquery.min.js"), "!function(){}();");
        Files.writeString(tempDir.resolve("app.js"), "const a = 1;");

        List<Path> files = walker.walk(tempDir).files();

        assertThat(files).extracting(p -> p.getFileName().toString()).containsExactly("app.js");
    }

    @Test
    @DisplayName("pathPrefix 지정 시 그 하위 경로 파일만 수집된다 — 국소분석")
    void pathPrefix_지정시_해당_경로만_수집() throws IOException {
        Files.createDirectories(tempDir.resolve("backend/src"));
        Files.createDirectories(tempDir.resolve("frontend/src"));
        Files.writeString(tempDir.resolve("backend/src/App.java"), "public class App {}");
        Files.writeString(tempDir.resolve("frontend/src/app.ts"), "export const x = 1;");

        WalkResult result = walker.walk(tempDir, "backend");

        List<String> rels = result.files().stream()
                .map(p -> tempDir.relativize(p).toString().replace('\\', '/'))
                .toList();
        assertThat(rels).containsExactly("backend/src/App.java");
        assertThat(result.totalEligible()).isEqualTo(1);
    }

    @Test
    @DisplayName("pathPrefix가 다른 디렉터리 이름의 부분 문자열이어도 오매칭하지 않는다 — 'src'가 'src2'를 포함하면 안 됨")
    void pathPrefix_부분문자열_오매칭_방지() throws IOException {
        Files.createDirectories(tempDir.resolve("src"));
        Files.createDirectories(tempDir.resolve("src2"));
        Files.writeString(tempDir.resolve("src/App.java"), "public class App {}");
        Files.writeString(tempDir.resolve("src2/Other.java"), "public class Other {}");

        WalkResult result = walker.walk(tempDir, "src");

        List<String> rels = result.files().stream()
                .map(p -> tempDir.relativize(p).toString().replace('\\', '/'))
                .toList();
        assertThat(rels).containsExactly("src/App.java");
    }

    @Test
    @DisplayName("pathPrefix 앞뒤 슬래시는 정규화되어 동일하게 매칭된다")
    void pathPrefix_슬래시_정규화() throws IOException {
        Files.createDirectories(tempDir.resolve("backend/src"));
        Files.writeString(tempDir.resolve("backend/src/App.java"), "public class App {}");

        WalkResult result = walker.walk(tempDir, "/backend/src/");

        List<String> rels = result.files().stream()
                .map(p -> tempDir.relativize(p).toString().replace('\\', '/'))
                .toList();
        assertThat(rels).containsExactly("backend/src/App.java");
    }

    @Test
    @DisplayName("pathPrefix가 null이면 전체 레포를 대상으로 한다(기존 동작 유지)")
    void pathPrefix_null이면_전체_대상() throws IOException {
        Files.createDirectories(tempDir.resolve("backend"));
        Files.createDirectories(tempDir.resolve("frontend"));
        Files.writeString(tempDir.resolve("backend/App.java"), "public class App {}");
        Files.writeString(tempDir.resolve("frontend/app.ts"), "export const x = 1;");

        WalkResult result = walker.walk(tempDir, (String) null);

        assertThat(result.files()).hasSize(2);
    }

    @Test
    @DisplayName("docsets 디렉터리와 *.docset 디렉터리는 순회하지 않는다")
    void docset_디렉토리_미수집() throws IOException {
        Files.createDirectories(tempDir.resolve("docs/docsets/Alamofire.docset/js"));
        Files.writeString(tempDir.resolve("docs/docsets/Alamofire.docset/js/jquery.js"), "var x=1;");
        Files.writeString(tempDir.resolve("main.js"), "const a = 1;");

        List<Path> files = walker.walk(tempDir).files();

        assertThat(files).extracting(p -> p.getFileName().toString()).containsExactly("main.js");
    }
}
