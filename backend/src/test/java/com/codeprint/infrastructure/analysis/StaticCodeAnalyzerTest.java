// StaticCodeAnalyzer 회귀 테스트 — DECISIONS_ANALYSIS.md에 기록된 버그 재발 방지
package com.codeprint.infrastructure.analysis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StaticCodeAnalyzerTest {

    private StaticCodeAnalyzer analyzer;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        analyzer = new StaticCodeAnalyzer();
    }

    // ── 회귀: 함수 주석 추출 ────────────────────────────────────────────────

    @Test
    @DisplayName("멀티라인 파라미터 Java 함수 위 주석을 추출한다")
    void 멀티라인_파라미터_함수_주석_추출() throws IOException {
        // DECISIONS_ANALYSIS.md: extractFunctionComments가 한 줄씩 스캔 시 '{' 없으면 패턴 매칭 실패했던 버그
        Path file = writeJavaFile("""
                package com.example;
                public class UserService {
                    // 사용자 ID로 사용자 조회
                    public User findById(
                        Long id,
                        boolean includeDeleted) {
                        return null;
                    }
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Java");

        assertThat(result.functionComments()).containsEntry("findById", "사용자 ID로 사용자 조회");
    }

    @Test
    @DisplayName("@어노테이션이 있어도 그 위 주석을 추출한다")
    void 어노테이션_건너뛰고_주석_추출() throws IOException {
        // DECISIONS_ANALYSIS.md: @Override 만나면 탐색 중단 → 주석이 null로 저장되던 버그
        Path file = writeJavaFile("""
                package com.example;
                public class UserServiceImpl implements UserService {
                    // 사용자 ID로 사용자 조회
                    @Override
                    public User findById(Long id) {
                        return null;
                    }
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Java");

        assertThat(result.functionComments()).containsEntry("findById", "사용자 ID로 사용자 조회");
    }

    @Test
    @DisplayName("여러 @어노테이션이 쌓여 있어도 그 위 주석을 추출한다")
    void 여러_어노테이션_건너뛰고_주석_추출() throws IOException {
        Path file = writeJavaFile("""
                package com.example;
                public class ProjectController {
                    // 프로젝트 목록 조회
                    @GetMapping
                    @PreAuthorize("isAuthenticated()")
                    public List<Project> getProjects() {
                        return null;
                    }
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Java");

        assertThat(result.functionComments()).containsEntry("getProjects", "프로젝트 목록 조회");
    }

    // ── 언어별 함수 추출 ────────────────────────────────────────────────────

    @Test
    @DisplayName("Java 파일에서 public/private 메서드명을 추출한다")
    void Java_함수_추출() throws IOException {
        Path file = writeJavaFile("""
                package com.example;
                public class AnalysisService {
                    public void startAnalysis(String repoUrl) {}
                    private void runInternal() {}
                    protected String getStatus() { return null; }
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Java");

        assertThat(result.functions()).containsExactlyInAnyOrder("startAnalysis", "runInternal", "getStatus");
    }

    @Test
    @DisplayName("TypeScript 파일에서 함수명을 추출한다")
    void TypeScript_함수_추출() throws IOException {
        Path file = writeTsFile("""
                const fetchProjects = async () => { return []; };
                function buildGraph(nodes: Node[]) {}
                const handleClick = (e: Event) => {};
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "TypeScript");

        assertThat(result.functions()).contains("fetchProjects", "buildGraph", "handleClick");
    }

    @Test
    @DisplayName("Python 파일에서 def 함수명을 추출한다")
    void Python_함수_추출() throws IOException {
        Path file = writePyFile("""
                def analyze_repo(path):
                    pass

                async def fetch_data(url):
                    pass

                def _internal_helper():
                    pass
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Python");

        assertThat(result.functions()).containsExactlyInAnyOrder("analyze_repo", "fetch_data", "_internal_helper");
    }

    // ── 파일 주석 추출 ──────────────────────────────────────────────────────

    @Test
    @DisplayName("파일 상단 // 주석을 파일 주석으로 추출한다")
    void 파일_상단_주석_추출() throws IOException {
        Path file = writeJavaFile("""
                // GitHub OAuth2 로그인 성공 후 JWT를 발급하는 핸들러
                package com.example;
                public class OAuth2Handler {}
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Java");

        assertThat(result.fileComment()).isEqualTo("GitHub OAuth2 로그인 성공 후 JWT를 발급하는 핸들러");
    }

    // ── import 추출 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Java import 경로를 추출한다")
    void Java_import_추출() throws IOException {
        Path file = writeJavaFile("""
                package com.example;
                import com.codeprint.domain.user.User;
                import java.util.List;
                public class UserService {}
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Java");

        assertThat(result.imports()).contains("com.codeprint.domain.user.User", "java.util.List");
    }

    // ── 헬퍼 ────────────────────────────────────────────────────────────────

    private Path writeJavaFile(String content) throws IOException {
        Path file = tempDir.resolve("TestFile.java");
        Files.writeString(file, content);
        return file;
    }

    private Path writeTsFile(String content) throws IOException {
        Path file = tempDir.resolve("testFile.ts");
        Files.writeString(file, content);
        return file;
    }

    private Path writePyFile(String content) throws IOException {
        Path file = tempDir.resolve("test_file.py");
        Files.writeString(file, content);
        return file;
    }
}
