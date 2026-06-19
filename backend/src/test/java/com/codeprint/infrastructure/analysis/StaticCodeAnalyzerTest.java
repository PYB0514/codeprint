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

    // ── 회귀: Java 인터페이스 메서드 추출 ──────────────────────────────────

    @Test
    @DisplayName("Java 인터페이스의 추상 메서드를 추출한다")
    void Java_인터페이스_추상_메서드_추출() throws IOException {
        // DECISIONS_ANALYSIS.md: 인터페이스 메서드는 public 키워드 없음 → getFunctionPattern 미인식으로 isInterfaceImpl 0개 버그
        Path file = writeJavaFile("""
                package com.codeprint.domain.graph;
                import java.util.List;
                import java.util.Optional;
                import java.util.UUID;
                public interface GraphRepository {
                    Graph save(Graph graph);
                    Optional<Graph> findById(UUID id);
                    List<Node> findNodesByGraphId(UUID graphId);
                    void deleteById(UUID id);
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Java");

        assertThat(result.functions())
                .containsExactlyInAnyOrder("save", "findById", "findNodesByGraphId", "deleteById");
    }

    @Test
    @DisplayName("Java 인터페이스의 default 메서드도 추출한다")
    void Java_인터페이스_default_메서드_추출() throws IOException {
        Path file = writeJavaFile("""
                package com.example;
                public interface UserRepository {
                    User findById(Long id);
                    default boolean exists(Long id) {
                        return findById(id) != null;
                    }
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Java");

        assertThat(result.functions()).contains("findById", "exists");
    }

    @Test
    @DisplayName("일반 클래스에서는 인터페이스 메서드 패턴이 추가 적용되지 않는다")
    void 일반_클래스는_인터페이스_패턴_미적용() throws IOException {
        Path file = writeJavaFile("""
                package com.example;
                public class UserServiceImpl {
                    public User findById(Long id) { return null; }
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Java");

        // 정상적으로 하나만 추출 (중복 없음)
        assertThat(result.functions()).containsExactly("findById");
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

    // ── functionCalls 추출 ─────────────────────────────────────────────────

    @Test
    @DisplayName("Java 메서드 내 함수 호출을 추출한다")
    void Java_함수_호출_추출() throws IOException {
        Path file = writeJavaFile("""
                package com.example;
                public class UserService {
                    public User createUser(String name) {
                        validate(name);
                        return save(new User(name));
                    }
                    private void validate(String name) {}
                    private User save(User user) { return user; }
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Java");

        assertThat(result.functionCalls()).containsKey("createUser");
        assertThat(result.functionCalls().get("createUser")).contains("validate", "save");
    }

    @Test
    @DisplayName("같은 파일 내 호출이라도 functionCalls에 포함된다")
    void Java_내부_호출_포함() throws IOException {
        Path file = writeJavaFile("""
                package com.example;
                public class GraphBuilder {
                    public void build() {
                        createNodes();
                        createEdges();
                    }
                    private void createNodes() {}
                    private void createEdges() {}
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Java");

        assertThat(result.functionCalls()).containsKey("build");
        assertThat(result.functionCalls().get("build")).contains("createNodes", "createEdges");
    }

    // ── C#/Go PascalCase 함수 호출 추출 ──────────────────────────────────────

    @Test
    @DisplayName("C# PascalCase 메서드 호출을 functionCalls에 추출한다")
    void CSharp_PascalCase_호출_추출() throws IOException {
        // 회귀: 호출 패턴이 소문자 시작만 인정해 C# PascalCase 메서드 호출이 0개였던 버그
        Path file = tempDir.resolve("UserService.cs");
        Files.writeString(file, """
                public class UserService {
                    public User CreateUser(string name) {
                        Validate(name);
                        return Save(name);
                    }
                    private void Validate(string name) {}
                    private User Save(string name) { return null; }
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "C#");

        assertThat(result.functionCalls()).containsKey("CreateUser");
        assertThat(result.functionCalls().get("CreateUser")).contains("Validate", "Save");
    }

    @Test
    @DisplayName("Go 대문자 시작 함수 호출을 functionCalls에 추출한다")
    void Go_대문자_함수_호출_추출() throws IOException {
        Path file = tempDir.resolve("main.go");
        Files.writeString(file, """
                package main

                func Run() {
                    CreateUser("a")
                    validate()
                }

                func CreateUser(name string) {}

                func validate() {}
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Go");

        assertThat(result.functionCalls()).containsKey("Run");
        assertThat(result.functionCalls().get("Run")).contains("CreateUser", "validate");
    }

    @Test
    @DisplayName("C# new 인스턴스화는 functionCalls에 호출로 포함되지 않는다")
    void CSharp_new_인스턴스화_제외() throws IOException {
        Path file = tempDir.resolve("Factory.cs");
        Files.writeString(file, """
                public class Factory {
                    public User Make() {
                        return new User();
                    }
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "C#");

        assertThat(result.functionCalls().getOrDefault("Make", java.util.List.of())).doesNotContain("User");
    }

    @Test
    @DisplayName("Java 소문자 호출 규칙은 영향받지 않는다 (회귀)")
    void Java_소문자_호출_규칙_유지() throws IOException {
        // C#/Go 분기 추가가 Java 호출 추출(대문자 생성자 제외)을 깨지 않는지 확인
        Path file = writeJavaFile("""
                package com.example;
                public class UserService {
                    public void run() {
                        validate();
                        User u = new User();
                    }
                    private void validate() {}
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Java");

        assertThat(result.functionCalls().get("run")).contains("validate");
        assertThat(result.functionCalls().getOrDefault("run", java.util.List.of())).doesNotContain("User");
    }

    // ── implements 추출 ────────────────────────────────────────────────────

    @Test
    @DisplayName("Java class implements 인터페이스 목록을 추출한다")
    void Java_implements_추출() throws IOException {
        Path file = writeJavaFile("""
                package com.example;
                import lombok.RequiredArgsConstructor;
                @Repository
                @RequiredArgsConstructor
                public class UserRepositoryImpl implements UserRepository {
                    public User save(User user) { return user; }
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Java");

        assertThat(result.implementedInterfaces()).containsExactly("UserRepository");
    }

    @Test
    @DisplayName("Java class implements 여러 인터페이스를 추출한다")
    void Java_implements_복수_추출() throws IOException {
        Path file = writeJavaFile("""
                package com.example;
                public class MyFilter implements Filter, Ordered {
                    public void doFilter() {}
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Java");

        assertThat(result.implementedInterfaces()).containsExactlyInAnyOrder("Filter", "Ordered");
    }

    @Test
    @DisplayName("implements 없는 클래스는 빈 목록을 반환한다")
    void Java_implements_없음() throws IOException {
        Path file = writeJavaFile("""
                package com.example;
                public class UserService {
                    public void create() {}
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Java");

        assertThat(result.implementedInterfaces()).isEmpty();
    }

    // ── TypeScript API 호출 추출 ──────────────────────────────────────────

    @Test
    @DisplayName("TypeScript axios API 호출 경로를 추출한다")
    void TypeScript_axios_API_호출_추출() throws IOException {
        Path file = writeTsFile("""
                import axios from 'axios';
                export const getProjects = () => axios.get('/api/projects');
                export const createProject = (data) => axios.post('/api/projects', data);
                export const deleteProject = (id) => axios.delete(`/api/projects/${id}`);
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "TypeScript");

        assertThat(result.apiCalls()).contains("GET:/api/projects", "POST:/api/projects");
        // 템플릿 리터럴 ${id} → * 정규화
        assertThat(result.apiCalls()).anyMatch(c -> c.startsWith("DELETE:/api/projects/"));
    }

    @Test
    @DisplayName("TypeScript axios PATCH/PUT 호출도 추출한다")
    void TypeScript_axios_PATCH_호출_추출() throws IOException {
        Path file = writeTsFile("""
                const updateUser = (id, data) => api.patch(`/api/admin/users/${id}/disable`, data);
                const replaceUser = (id, data) => api.put(`/api/users/${id}`, data);
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "TypeScript");

        assertThat(result.apiCalls()).anyMatch(c -> c.startsWith("PATCH:"));
        assertThat(result.apiCalls()).anyMatch(c -> c.startsWith("PUT:"));
    }

    // ── fetch() API 호출 추출 ─────────────────────────────────────────────

    @Test
    @DisplayName("fetch() 호출은 method 옵션이 없으면 GET으로 추출한다")
    void fetch_기본_GET_추출() throws IOException {
        Path file = writeTsFile("""
                export const getProjects = async () => {
                    const res = await fetch('/api/projects');
                    return res.json();
                };
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "TypeScript");

        assertThat(result.apiCalls()).contains("GET:/api/projects");
    }

    @Test
    @DisplayName("fetch() 옵션 객체의 method를 HTTP 메서드로 추출한다")
    void fetch_method_옵션_추출() throws IOException {
        Path file = writeTsFile("""
                export const createProject = async (data: unknown) => {
                    const res = await fetch('/api/projects', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify(data),
                    });
                    return res.json();
                };
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "TypeScript");

        assertThat(result.apiCalls()).contains("POST:/api/projects");
    }

    @Test
    @DisplayName("fetch() 템플릿 리터럴 경로의 ${}를 *로 정규화한다")
    void fetch_템플릿_리터럴_정규화() throws IOException {
        Path file = writeTsFile("""
                export const deleteProject = (id: string) =>
                    fetch(`/api/projects/${id}`, { method: 'DELETE' });
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "TypeScript");

        assertThat(result.apiCalls()).contains("DELETE:/api/projects/*");
    }

    @Test
    @DisplayName("외부 URL fetch()는 API 호출로 추출하지 않는다")
    void fetch_외부_URL_제외() throws IOException {
        Path file = writeTsFile("""
                const checkStatus = () => fetch('https://status.example.com/health');
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "TypeScript");

        assertThat(result.apiCalls()).isEmpty();
    }

    @Test
    @DisplayName("연속된 fetch() 호출에서 다음 호출의 method를 가져오지 않는다")
    void fetch_연속_호출_method_혼동_방지() throws IOException {
        Path file = writeTsFile("""
                const a = () => fetch('/api/first');
                const b = () => fetch('/api/second', { method: 'DELETE' });
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "TypeScript");

        assertThat(result.apiCalls()).contains("GET:/api/first", "DELETE:/api/second");
    }

    // ── DB 테이블 추출 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Java @Entity @Table(name=) 어노테이션에서 테이블명을 추출한다")
    void Java_Entity_Table_어노테이션_추출() throws IOException {
        Path file = writeJavaFile("""
                package com.example;
                import jakarta.persistence.*;
                @Entity
                @Table(name = "users")
                public class User {
                    @Id private Long id;
                    private String email;
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Java");

        assertThat(result.dbTables()).hasSize(1);
        assertThat(result.dbTables().get(0).tableName()).isEqualTo("users");
    }

    @Test
    @DisplayName("Java @Entity만 있고 @Table 없으면 클래스명을 테이블명으로 사용한다")
    void Java_Entity_클래스명_폴백() throws IOException {
        Path file = writeJavaFile("""
                package com.example;
                import jakarta.persistence.*;
                @Entity
                public class Project {
                    @Id private Long id;
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Java");

        assertThat(result.dbTables()).hasSize(1);
        assertThat(result.dbTables().get(0).tableName()).isEqualTo("Project");
    }

    // ── JpaRepository 엔티티 추출 ─────────────────────────────────────────

    @Test
    @DisplayName("JpaRepository<EntityName, ID>에서 엔티티 클래스명을 추출한다")
    void Java_JpaRepository_엔티티_추출() throws IOException {
        Path file = writeJavaFile("""
                package com.example;
                import org.springframework.data.jpa.repository.JpaRepository;
                public interface UserJpaRepository extends JpaRepository<User, Long> {
                    User findByEmail(String email);
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Java");

        assertThat(result.repositoryEntityClass()).isEqualTo("User");
    }

    // ── 컨트롤러 매핑 추출 ────────────────────────────────────────────────

    @Test
    @DisplayName("Java @RequestMapping + @GetMapping 조합으로 전체 경로를 합성한다")
    void Java_컨트롤러_매핑_합성() throws IOException {
        Path file = writeJavaFile("""
                package com.example;
                @RestController
                @RequestMapping("/api/projects")
                public class ProjectController {
                    @GetMapping
                    public List<Project> list() { return null; }
                    @PostMapping
                    public Project create() { return null; }
                    @DeleteMapping("/{id}")
                    public void delete() {}
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Java");

        assertThat(result.controllerMappings()).contains("/api/projects");
        assertThat(result.controllerMappings()).contains("/api/projects/{id}");
    }

    // ── 나머지 언어 함수 추출 ─────────────────────────────────────────────

    @Test
    @DisplayName("Kotlin 파일에서 fun 함수명을 추출한다")
    void Kotlin_함수_추출() throws IOException {
        Path file = tempDir.resolve("UserService.kt");
        Files.writeString(file, """
                class UserService {
                    fun createUser(name: String): User {
                        return User(name)
                    }
                    private fun validate(name: String) {}
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Kotlin");

        assertThat(result.functions()).containsExactlyInAnyOrder("createUser", "validate");
    }

    @Test
    @DisplayName("Go 파일에서 func 함수명을 추출한다")
    void Go_함수_추출() throws IOException {
        Path file = tempDir.resolve("user.go");
        Files.writeString(file, """
                package main

                func CreateUser(name string) *User {
                    return &User{Name: name}
                }

                func (u *User) Validate() bool {
                    return u.Name != ""
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Go");

        assertThat(result.functions()).contains("CreateUser", "Validate");
    }

    @Test
    @DisplayName("Go 타입 전용 리시버 메서드 func (T) M() 도 함수로 추출한다 + 본문 호출 스캔")
    void Go_타입전용_리시버_추출() throws IOException {
        // 회귀: 리시버 변수명 없는 func (jsonBinding) Bind() 가 미추출되어 본문의 decodeJSON 호출이 누락 → DEAD_CODE 오탐
        Path file = tempDir.resolve("json.go");
        Files.writeString(file, """
                package binding

                func (jsonBinding) Bind(req *http.Request, obj any) error {
                    return decodeJSON(req.Body, obj)
                }

                func (*xmlBinding) Name() string {
                    return "xml"
                }

                func decodeJSON(r io.Reader, obj any) error {
                    return nil
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Go");

        assertThat(result.functions()).contains("Bind", "Name", "decodeJSON");
        assertThat(result.functionCalls()).containsKey("Bind");
        assertThat(result.functionCalls().get("Bind")).contains("decodeJSON");
    }

    @Test
    @DisplayName("Go 이름 있는 리시버 메서드 func (s *Server) M() 추출은 유지된다 (회귀)")
    void Go_이름있는_리시버_유지() throws IOException {
        Path file = tempDir.resolve("server.go");
        Files.writeString(file, """
                package main

                func (engine *Engine) addRoute(method string) {
                    root.insert(method)
                }

                func (s Server) handle() {}
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Go");

        assertThat(result.functions()).contains("addRoute", "handle");
    }

    @Test
    @DisplayName("Go import는 import 문/블록 내부만 추출 — 임의 문자열 리터럴은 제외")
    void Go_import_문자열리터럴_제외() throws IOException {
        // 회귀: import 정규식이 모든 따옴표 문자열을 import로 오인 → 같은 패키지 파일명과 매칭돼 거짓 CYCLIC_IMPORT
        Path file = tempDir.resolve("uri.go");
        Files.writeString(file, """
                package binding

                import (
                    "io"
                    "github.com/gin-gonic/gin/codec/json"
                )

                func (uriBinding) Name() string {
                    return "uri"
                }

                func (uriBinding) BindUri(m map[string][]string, obj any) error {
                    contentType := "application/json"
                    return mapURI(obj, m, "query")
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Go");

        assertThat(result.imports()).contains("io", "github.com/gin-gonic/gin/codec/json");
        assertThat(result.imports()).doesNotContain("uri", "query", "application/json");
    }

    @Test
    @DisplayName("Rust 파일에서 fn 함수명을 추출한다")
    void Rust_함수_추출() throws IOException {
        Path file = tempDir.resolve("lib.rs");
        Files.writeString(file, """
                pub fn create_user(name: &str) -> User {
                    User { name: name.to_string() }
                }

                fn validate_name(name: &str) -> bool {
                    !name.is_empty()
                }

                pub async fn fetch_data() -> Vec<u8> {
                    vec![]
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Rust");

        assertThat(result.functions()).containsExactlyInAnyOrder("create_user", "validate_name", "fetch_data");
    }

    @Test
    @DisplayName("Ruby 파일에서 def 메서드명을 추출한다")
    void Ruby_함수_추출() throws IOException {
        Path file = tempDir.resolve("user_service.rb");
        Files.writeString(file, """
                class UserService
                  def create_user(name)
                    save(name)
                  end

                  def valid?(name)
                    !name.nil?
                  end

                  private

                  def save(name)
                    name
                  end
                end
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Ruby");

        assertThat(result.functions()).containsExactlyInAnyOrder("create_user", "valid?", "save");
    }

    @Test
    @DisplayName("PHP 파일에서 function 메서드명을 추출한다")
    void PHP_함수_추출() throws IOException {
        Path file = tempDir.resolve("UserService.php");
        Files.writeString(file, """
                <?php
                class UserService {
                    public function createUser($name) {
                        return $this->save($name);
                    }
                    private function save($name) {
                        return $name;
                    }
                    protected static function validate($name) {}
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "PHP");

        assertThat(result.functions()).containsExactlyInAnyOrder("createUser", "save", "validate");
    }

    @Test
    @DisplayName("Swift 파일에서 func 함수명을 추출한다")
    void Swift_함수_추출() throws IOException {
        Path file = tempDir.resolve("UserService.swift");
        Files.writeString(file, """
                class UserService {
                    func createUser(name: String) -> User {
                        return User(name: name)
                    }
                    private func validate(name: String) -> Bool {
                        return !name.isEmpty
                    }
                    static func shared() -> UserService {
                        return UserService()
                    }
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Swift");

        assertThat(result.functions()).containsExactlyInAnyOrder("createUser", "validate", "shared");
    }

    @Test
    @DisplayName("C# 파일에서 메서드명을 추출한다")
    void CSharp_함수_추출() throws IOException {
        Path file = tempDir.resolve("UserService.cs");
        Files.writeString(file, """
                public class UserService {
                    public User CreateUser(string name) {
                        return Save(name);
                    }
                    private User Save(string name) {
                        return new User(name);
                    }
                    protected virtual void Validate(string name) {}
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "C#");

        assertThat(result.functions()).containsExactlyInAnyOrder("CreateUser", "Save", "Validate");
    }

    @Test
    @DisplayName("JavaScript 파일에서 함수명을 추출한다")
    void JavaScript_함수_추출() throws IOException {
        Path file = tempDir.resolve("api.js");
        Files.writeString(file, """
                const fetchData = async () => { return []; };
                function buildGraph(nodes) {}
                const handleSubmit = (e) => { e.preventDefault(); };
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "JavaScript");

        assertThat(result.functions()).contains("fetchData", "buildGraph", "handleSubmit");
    }

    // ── JSX 컴포넌트 추출 (회귀: DEAD_CODE 오탐 방지) ──────────────────────

    @Test
    @DisplayName("TSX 파일에서 JSX 컴포넌트 사용을 추출한다")
    void TSX_JSX_컴포넌트_추출() throws IOException {
        // DEAD_CODE 오탐 근본 원인: JSX 렌더링을 FUNCTION_CALL로 인식 못 함
        Path file = tempDir.resolve("DashboardPage.tsx");
        Files.writeString(file, """
                import React from 'react';
                import ProjectCard from './ProjectCard';
                import AppHeader from './AppHeader';
                const DashboardPage = () => (
                  <div>
                    <AppHeader />
                    <ProjectCard title="test" />
                    <span>plain html</span>
                  </div>
                );
                export default DashboardPage;
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "TypeScript");

        assertThat(result.jsxComponents()).contains("AppHeader", "ProjectCard");
        assertThat(result.jsxComponents()).doesNotContain("span");
    }

    @Test
    @DisplayName("JSX 파일에서도 JSX 컴포넌트를 추출한다")
    void JSX_파일_컴포넌트_추출() throws IOException {
        Path file = tempDir.resolve("App.jsx");
        Files.writeString(file, """
                import React from 'react';
                function App() {
                  return <Router><HomePage /></Router>;
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "JavaScript");

        assertThat(result.jsxComponents()).contains("Router", "HomePage");
    }

    @Test
    @DisplayName("TS 파일 (비JSX)에서는 jsxComponents가 비어 있다")
    void TS_파일은_JSX_컴포넌트_없음() throws IOException {
        Path file = writeTsFile("""
                export const fetchData = async () => [];
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "TypeScript");

        assertThat(result.jsxComponents()).isEmpty();
    }

    // ── 다국어 API 엔드포인트 감지 ────────────────────────────────────────────

    @Test
    @DisplayName("Express.js router.get/post에서 API 경로를 추출한다")
    void Express_API_엔드포인트_추출() throws IOException {
        Path file = tempDir.resolve("userRouter.js");
        Files.writeString(file, """
                const express = require('express');
                const router = express.Router();
                router.get('/users', getUsers);
                router.post('/users', createUser);
                router.delete('/users/:id', deleteUser);
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "JavaScript");

        assertThat(result.controllerMappings()).contains("GET:/users", "POST:/users", "DELETE:/users/:id");
    }

    @Test
    @DisplayName("NestJS @Controller prefix + @Get/@Post 데코레이터를 합성해 API 경로를 추출한다")
    void NestJS_엔드포인트_추출() throws IOException {
        Path file = tempDir.resolve("cats.controller.ts");
        Files.writeString(file, """
                import { Controller, Get, Post } from '@nestjs/common';

                @Controller('cats')
                export class CatsController {
                    @Get()
                    findAll() {}

                    @Get(':id')
                    findOne() {}

                    @Post()
                    create() {}
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "TypeScript");

        assertThat(result.controllerMappings()).contains("GET:/cats", "GET:/cats/:id", "POST:/cats");
    }

    @Test
    @DisplayName("NestJS @Controller가 없는 파일에서는 @Get/@Post를 엔드포인트로 잡지 않는다")
    void NestJS_컨트롤러_없으면_미추출() throws IOException {
        Path file = tempDir.resolve("not-a-controller.ts");
        Files.writeString(file, """
                class Foo {
                    @Get('bar')
                    something() {}
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "TypeScript");

        assertThat(result.controllerMappings()).isEmpty();
    }

    @Test
    @DisplayName("FastAPI @app.get/post에서 API 경로를 추출한다")
    void FastAPI_엔드포인트_추출() throws IOException {
        Path file = writePyFile("""
                from fastapi import FastAPI
                app = FastAPI()

                @app.get("/items")
                async def get_items():
                    pass

                @app.post("/items")
                async def create_item():
                    pass

                @router.put("/items/{item_id}")
                async def update_item():
                    pass
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Python");

        assertThat(result.controllerMappings()).contains("GET:/items", "POST:/items", "PUT:/items/{item_id}");
    }

    @Test
    @DisplayName("Go Gin r.GET/POST에서 API 경로를 추출한다")
    void GoGin_엔드포인트_추출() throws IOException {
        Path file = tempDir.resolve("main.go");
        Files.writeString(file, """
                package main
                import "github.com/gin-gonic/gin"
                func main() {
                    r := gin.Default()
                    r.GET("/users", getUsers)
                    r.POST("/users", createUser)
                    r.DELETE("/users/:id", deleteUser)
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Go");

        assertThat(result.controllerMappings()).contains("GET:/users", "POST:/users", "DELETE:/users/:id");
    }

    @Test
    @DisplayName("C# ASP.NET Core [HttpGet] 어노테이션에서 API 경로를 추출한다")
    void CSharp_API_엔드포인트_추출() throws IOException {
        Path file = tempDir.resolve("UserController.cs");
        Files.writeString(file, """
                [ApiController]
                [Route("api/[controller]")]
                public class UserController : ControllerBase {
                    [HttpGet]
                    public IActionResult List() => Ok();
                    [HttpPost]
                    public IActionResult Create() => Ok();
                    [HttpDelete("{id}")]
                    public IActionResult Delete(int id) => Ok();
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "C#");

        assertThat(result.controllerMappings()).anyMatch(m -> m.startsWith("GET:api/user"));
        assertThat(result.controllerMappings()).anyMatch(m -> m.startsWith("POST:api/user"));
        assertThat(result.controllerMappings()).anyMatch(m -> m.startsWith("DELETE:"));
    }

    @Test
    @DisplayName("C# Entity Framework DbSet<T>에서 DB 테이블명을 추출한다")
    void CSharp_DbSet_DB테이블_추출() throws IOException {
        Path file = tempDir.resolve("AppDbContext.cs");
        Files.writeString(file, """
                public class AppDbContext : DbContext {
                    public DbSet<User> Users { get; set; }
                    public DbSet<Project> Projects { get; set; }
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "C#");

        assertThat(result.dbTables()).extracting(DbTableInfo::tableName)
                .containsExactlyInAnyOrder("Users", "Projects");
    }

    // ── 다국어 DB 엔티티 감지 ────────────────────────────────────────────────

    @Test
    @DisplayName("TypeORM @Entity() 데코레이터에서 DB 테이블명을 추출한다")
    void TypeORM_Entity_추출() throws IOException {
        Path file = tempDir.resolve("User.ts");
        Files.writeString(file, """
                import { Entity, Column, PrimaryGeneratedColumn } from 'typeorm';
                @Entity('users')
                export class User {
                    @PrimaryGeneratedColumn()
                    id: number;
                    @Column()
                    name: string;
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "TypeScript");

        assertThat(result.dbTables()).hasSize(1);
        assertThat(result.dbTables().get(0).tableName()).isEqualTo("users");
    }

    @Test
    @DisplayName("SQLAlchemy Base 상속 클래스에서 DB 테이블명을 추출한다")
    void SQLAlchemy_Model_추출() throws IOException {
        Path file = writePyFile("""
                from sqlalchemy import Column, String
                from database import Base

                class User(Base):
                    __tablename__ = 'users'
                    id = Column(String, primary_key=True)
                    name = Column(String)
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Python");

        assertThat(result.dbTables()).hasSize(1);
        assertThat(result.dbTables().get(0).tableName()).isEqualTo("users");
    }

    // ── Django ORM / URL 라우팅 감지 ─────────────────────────────────────────

    @Test
    @DisplayName("Django models.Model 상속 클래스에서 DB 테이블명(소문자 클래스명)을 추출한다")
    void Django_Model_추출() throws IOException {
        Path file = writePyFile("""
                from django.db import models

                class BlogPost(models.Model):
                    title = models.CharField(max_length=200)

                class Comment(models.Model):
                    body = models.TextField()
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Python");

        assertThat(result.dbTables()).extracting(DbTableInfo::tableName)
                .containsExactlyInAnyOrder("blogpost", "comment");
    }

    @Test
    @DisplayName("Django Meta.db_table이 규칙 기반 테이블명을 덮어쓴다")
    void Django_Meta_db_table_우선() throws IOException {
        Path file = writePyFile("""
                from django.db import models

                class User(models.Model):
                    name = models.CharField(max_length=100)

                    class Meta:
                        db_table = 'auth_user'
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Python");

        assertThat(result.dbTables()).hasSize(1);
        assertThat(result.dbTables().get(0).tableName()).isEqualTo("auth_user");
        assertThat(result.dbTables().get(0).className()).isEqualTo("User");
    }

    @Test
    @DisplayName("Django 추상 모델(abstract = True)은 DB 테이블로 추출하지 않는다")
    void Django_추상모델_제외() throws IOException {
        Path file = writePyFile("""
                from django.db import models

                class TimeStamped(models.Model):
                    created_at = models.DateTimeField(auto_now_add=True)

                    class Meta:
                        abstract = True

                class Article(models.Model):
                    title = models.CharField(max_length=200)
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Python");

        assertThat(result.dbTables()).extracting(DbTableInfo::tableName)
                .containsExactly("article");
    }

    @Test
    @DisplayName("Django urls.py path/re_path에서 API 경로를 추출한다(메서드 불명 → GET)")
    void Django_URL_라우팅_추출() throws IOException {
        Path file = writePyFile("""
                from django.urls import path, re_path
                from . import views

                urlpatterns = [
                    path('api/articles/', views.article_list),
                    path('api/articles/<int:pk>/', views.article_detail),
                    re_path(r'^api/health$', views.health),
                ]
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Python");

        assertThat(result.controllerMappings()).contains(
                "GET:/api/articles/",
                "GET:/api/articles/<int:pk>/",
                "GET:/api/health");
    }

    // ── Prisma 스키마 DB 테이블 감지 ─────────────────────────────────────────

    @Test
    @DisplayName("schema.prisma model 블록에서 DB 테이블을 추출한다")
    void Prisma_model_블록_추출() throws IOException {
        Path file = tempDir.resolve("schema.prisma");
        Files.writeString(file, """
                generator client {
                  provider = "prisma-client-js"
                }

                model User {
                  id    Int    @id @default(autoincrement())
                  email String @unique
                }

                model Post {
                  id       Int  @id @default(autoincrement())
                  author   User @relation(fields: [authorId], references: [id])
                  authorId Int
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Prisma");

        assertThat(result.dbTables()).extracting(DbTableInfo::tableName)
                .containsExactlyInAnyOrder("User", "Post");
    }

    // ── Go DB 테이블 감지 (GORM / Beego ORM) ─────────────────────────────────

    @Test
    @DisplayName("Go GORM gorm.Model 임베딩 구조체에서 DB 테이블명을 추출한다")
    void Go_GORM_Model_임베딩_추출() throws IOException {
        Path file = tempDir.resolve("models.go");
        Files.writeString(file, """
                package models

                import "gorm.io/gorm"

                type User struct {
                    gorm.Model
                    Name  string
                    Email string
                }

                type OrderItem struct {
                    gorm.Model
                    Quantity int
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Go");

        assertThat(result.dbTables()).extracting(DbTableInfo::tableName)
                .containsExactlyInAnyOrder("users", "order_items");
    }

    @Test
    @DisplayName("Go GORM TableName() 오버라이드가 규칙 기반 테이블명을 덮어쓴다")
    void Go_GORM_TableName_오버라이드_우선() throws IOException {
        Path file = tempDir.resolve("user.go");
        Files.writeString(file, """
                package models

                import "gorm.io/gorm"

                type User struct {
                    gorm.Model
                    Name string
                }

                func (u *User) TableName() string {
                    return "members"
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Go");

        assertThat(result.dbTables()).hasSize(1);
        assertThat(result.dbTables().get(0).tableName()).isEqualTo("members");
        assertThat(result.dbTables().get(0).className()).isEqualTo("User");
    }

    @Test
    @DisplayName("Go Beego orm.RegisterModel에서 DB 테이블명을 추출한다")
    void Go_Beego_RegisterModel_추출() throws IOException {
        Path file = tempDir.resolve("init.go");
        Files.writeString(file, """
                package models

                import "github.com/beego/beego/v2/client/orm"

                func init() {
                    orm.RegisterModel(new(User), new(Profile))
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Go");

        assertThat(result.dbTables()).extracting(DbTableInfo::tableName)
                .containsExactlyInAnyOrder("user", "profile");
    }

    @Test
    @DisplayName("gorm.Model 없는 일반 Go 구조체는 DB 테이블로 감지하지 않는다")
    void Go_일반_구조체_미감지() throws IOException {
        Path file = tempDir.resolve("config.go");
        Files.writeString(file, """
                package config

                type ServerConfig struct {
                    Port int
                    Host string
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Go");

        assertThat(result.dbTables()).isEmpty();
    }

    // ── 다국어 비동기 메서드 감지 ────────────────────────────────────────────

    @Test
    @DisplayName("Python async def 함수명을 asyncMethods에 포함한다")
    void Python_async_def_감지() throws IOException {
        Path file = writePyFile("""
                async def fetch_data(url):
                    pass

                def sync_func():
                    pass

                async def process_items():
                    pass
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Python");

        assertThat(result.asyncMethods()).containsExactlyInAnyOrder("fetch_data", "process_items");
        assertThat(result.asyncMethods()).doesNotContain("sync_func");
    }

    @Test
    @DisplayName("Java @Async 어노테이션 메서드를 asyncMethods에 포함한다")
    void Java_Async_어노테이션_감지() throws IOException {
        Path file = writeJavaFile("""
                public class Mailer {
                    @Async
                    public void sendEmail(String to) {}

                    public void syncSend(String to) {}
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Java");

        assertThat(result.asyncMethods()).contains("sendEmail");
        assertThat(result.asyncMethods()).doesNotContain("syncSend");
    }

    @Test
    @DisplayName("주석·문자열 속 @Async 텍스트는 asyncMethods로 오인하지 않는다 (B-15)")
    void Java_주석_속_Async_텍스트_제외() throws IOException {
        // B-15: 정규식이 줄 중간(주석 //·문자열 ")의 @Async 텍스트를 어노테이션으로 오인 → 다음 메서드 오탐
        Path file = writeJavaFile("""
                public class WarningService {
                    // @Async 프록시 우회는 JVM 언어에만 해당
                    private boolean isProxyAsyncLanguage(String lang) { return false; }

                    private void report() {
                        String msg = "@Async 자기 호출 감지";
                    }
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Java");

        assertThat(result.asyncMethods()).doesNotContain("isProxyAsyncLanguage", "report");
    }

    @Test
    @DisplayName("TypeScript async function을 asyncMethods에 포함한다")
    void TypeScript_async_function_감지() throws IOException {
        Path file = writeTsFile("""
                async function fetchProjects(): Promise<Project[]> {
                    return [];
                }
                const loadUser = async () => { return null; };
                function syncHelper() {}
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "TypeScript");

        assertThat(result.asyncMethods()).contains("fetchProjects", "loadUser");
        assertThat(result.asyncMethods()).doesNotContain("syncHelper");
    }

    // ── Phase B-9: raw SQL DB 감지 ────────────────────────────────────────

    @Test
    @DisplayName("Java JDBC raw SQL SELECT FROM에서 DB_TABLE READ 감지")
    void Java_JDBC_SELECT_감지() throws IOException {
        Path file = writeJavaFile("""
                package com.example;
                public class UserDao {
                    public User findById(long id) {
                        String sql = "SELECT * FROM users WHERE id = ?";
                        return jdbcTemplate.queryForObject(sql, rowMapper, id);
                    }
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Java");

        assertThat(result.rawSqlAccesses()).anyMatch(a -> a.tableName().equals("users") && !a.isWrite());
    }

    @Test
    @DisplayName("Go database/sql INSERT INTO에서 DB_TABLE WRITE 감지")
    void Go_INSERT_INTO_감지() throws IOException {
        Path file = tempDir.resolve("user_repo.go");
        Files.writeString(file, """
                package repo

                func (r *UserRepo) Create(u *User) error {
                    _, err := r.db.Exec("INSERT INTO users (name, email) VALUES (?, ?)", u.Name, u.Email)
                    return err
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Go");

        assertThat(result.rawSqlAccesses()).anyMatch(a -> a.tableName().equals("users") && a.isWrite());
    }

    @Test
    @DisplayName("Python raw SQL UPDATE에서 WRITE 감지")
    void Python_UPDATE_감지() throws IOException {
        Path file = writePyFile("""
                def update_user(conn, user_id, name):
                    cursor = conn.cursor()
                    cursor.execute("UPDATE users SET name = %s WHERE id = %s", (name, user_id))
                    conn.commit()
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Python");

        assertThat(result.rawSqlAccesses()).anyMatch(a -> a.tableName().equals("users") && a.isWrite());
    }

    @Test
    @DisplayName("C# Dapper raw SQL SELECT에서 READ 감지")
    void CSharp_Dapper_SELECT_감지() throws IOException {
        Path file = tempDir.resolve("UserRepo.cs");
        Files.writeString(file, """
                public class UserRepo {
                    public IEnumerable<User> GetAll() {
                        return conn.Query<User>("SELECT * FROM users");
                    }
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "C#");

        assertThat(result.rawSqlAccesses()).anyMatch(a -> a.tableName().equals("users") && !a.isWrite());
    }

    @Test
    @DisplayName("raw SQL DELETE FROM에서 WRITE 감지")
    void DELETE_FROM_감지() throws IOException {
        Path file = writeJavaFile("""
                package com.example;
                public class CleanupJob {
                    public void run() {
                        jdbc.execute("DELETE FROM expired_tokens WHERE created_at < ?");
                    }
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Java");

        assertThat(result.rawSqlAccesses()).anyMatch(a -> a.tableName().equals("expired_tokens") && a.isWrite());
    }

    @Test
    @DisplayName("같은 파일에서 여러 테이블을 읽고 쓰는 raw SQL을 모두 감지한다")
    void 복수_테이블_raw_SQL_감지() throws IOException {
        Path file = writeJavaFile("""
                package com.example;
                public class OrderDao {
                    public void process() {
                        jdbc.query("SELECT * FROM orders WHERE status = ?", rowMapper, "PENDING");
                        jdbc.update("INSERT INTO payments (order_id, amount) VALUES (?, ?)", orderId, amount);
                        jdbc.query("SELECT id FROM users WHERE active = 1", rowMapper2);
                    }
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Java");

        assertThat(result.rawSqlAccesses())
            .anyMatch(a -> a.tableName().equals("orders") && !a.isWrite())
            .anyMatch(a -> a.tableName().equals("payments") && a.isWrite())
            .anyMatch(a -> a.tableName().equals("users") && !a.isWrite());
    }

    // ── 회귀: raw SQL 산문 오검출 차단 (B-9 정밀화 — 앵커 + 강한 마커) ────────

    @Test
    @DisplayName("SQL 동사로 시작하지 않는 산문 문자열은 raw SQL로 오검출하지 않는다")
    void raw_SQL_산문_미시작_오검출_차단() throws IOException {
        Path file = writeJavaFile("""
                package com.example;
                public class Messages {
                    String a = "Please select your name from the list";
                    String b = "Failed to delete from disk during cleanup";
                    String c = "to use to select a connection from a given pool";
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Java");

        assertThat(result.rawSqlAccesses()).isEmpty();
    }

    @Test
    @DisplayName("SQL 동사로 시작해도 강한 SQL 마커가 없는 산문은 오검출하지 않는다")
    void raw_SQL_마커_없는_산문_오검출_차단() throws IOException {
        Path file = writeJavaFile("""
                package com.example;
                public class Labels {
                    String a = "Select country from dropdown menu";
                    String b = "insert into queue before flush";
                    String c = "update them set aside for later";
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Java");

        assertThat(result.rawSqlAccesses()).isEmpty();
    }

    @Test
    @DisplayName("WHERE 절의 문자열 값에 든 다른 SQL 동사를 추가 테이블로 오검출하지 않는다")
    void raw_SQL_문자열값_내_동사_오검출_차단() throws IOException {
        Path file = writeJavaFile("""
                package com.example;
                public class AuditDao {
                    public void find() {
                        jdbc.query("SELECT * FROM audit_log WHERE action = 'delete from cache'", rowMapper);
                    }
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Java");

        assertThat(result.rawSqlAccesses())
            .anyMatch(a -> a.tableName().equals("audit_log") && !a.isWrite())
            .noneMatch(a -> a.tableName().equals("cache"));
    }

    // ── 회귀: 프레임워크 어노테이션 메서드 추출 (C-13 DEAD_CODE 오탐 수정) ────────

    @Test
    @DisplayName("Java 프레임워크 어노테이션(@GetMapping·@InitBinder·@Bean·@Override) 메서드를 추출한다")
    void 프레임워크_어노테이션_메서드_추출_Java() throws IOException {
        Path file = writeJavaFile("""
                package com.example;
                public class OwnerController {
                    @GetMapping("/owners/new")
                    public String initCreationForm(Map<String, Object> model) { return "x"; }

                    @InitBinder
                    public void setAllowedFields(WebDataBinder dataBinder) { }

                    @Bean
                    public LocaleResolver localeResolver() { return null; }

                    @Override
                    public void addInterceptors(InterceptorRegistry registry) { }

                    public String notAnnotated() { return "y"; }
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Java");

        assertThat(result.frameworkAnnotatedMethods())
                .contains("initCreationForm", "setAllowedFields", "localeResolver", "addInterceptors")
                .doesNotContain("notAnnotated");
    }

    @Test
    @DisplayName("Python 데코레이터(@property·@app.route) 부착 def를 추출한다")
    void 데코레이터_Python_def_추출() throws IOException {
        Path file = writePyFile("""
                class Api:
                    @property
                    def status(self):
                        return self._status

                    @app.route("/health")
                    def health():
                        return "ok"

                    def plain(self):
                        return 1
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Python");

        assertThat(result.frameworkAnnotatedMethods())
                .contains("status", "health")
                .doesNotContain("plain");
    }

    // ── Ruby / PHP / Swift DB·API 감지 ────────────────────────────────────────

    @Test
    @DisplayName("Ruby ActiveRecord 상속 클래스에서 DB 테이블명을 추출한다")
    void Ruby_ActiveRecord_DB테이블_추출() throws IOException {
        Path file = tempDir.resolve("user.rb");
        Files.writeString(file, """
                class User < ApplicationRecord
                end

                class OrderItem < ActiveRecord::Base
                end
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Ruby");

        assertThat(result.dbTables()).extracting(DbTableInfo::tableName)
                .containsExactlyInAnyOrder("users", "order_items");
    }

    @Test
    @DisplayName("Ruby Rails routes.rb에서 API 엔드포인트를 추출한다")
    void Ruby_Rails_라우팅_API_추출() throws IOException {
        Path file = tempDir.resolve("routes.rb");
        Files.writeString(file, """
                Rails.application.routes.draw do
                  get '/users', to: 'users#index'
                  post '/users', to: 'users#create'
                  delete '/users/:id', to: 'users#destroy'
                end
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Ruby");

        assertThat(result.controllerMappings())
                .contains("GET:/users", "POST:/users", "DELETE:/users/:id");
    }

    @Test
    @DisplayName("PHP Eloquent Model 상속 클래스에서 DB 테이블명을 추출한다")
    void PHP_Eloquent_DB테이블_추출() throws IOException {
        Path file = tempDir.resolve("User.php");
        Files.writeString(file, """
                <?php
                class User extends Model
                {
                }

                class Post extends Eloquent
                {
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "PHP");

        assertThat(result.dbTables()).extracting(DbTableInfo::tableName)
                .containsExactlyInAnyOrder("users", "posts");
    }

    @Test
    @DisplayName("PHP Laravel Route::get/post에서 API 엔드포인트를 추출한다")
    void PHP_Laravel_라우팅_API_추출() throws IOException {
        Path file = tempDir.resolve("routes.php");
        Files.writeString(file, """
                <?php
                Route::get('/users', [UserController::class, 'index']);
                Route::post('/users', [UserController::class, 'store']);
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "PHP");

        assertThat(result.controllerMappings())
                .contains("GET:/users", "POST:/users");
    }

    @Test
    @DisplayName("Swift NSManagedObject 상속 클래스에서 DB 테이블명을 추출한다")
    void Swift_CoreData_NSManagedObject_추출() throws IOException {
        Path file = tempDir.resolve("User+CoreDataClass.swift");
        Files.writeString(file, """
                import CoreData

                class User: NSManagedObject {
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Swift");

        assertThat(result.dbTables()).extracting(DbTableInfo::tableName)
                .contains("user");
    }

    @Test
    @DisplayName("Swift Vapor router.get/post에서 API 엔드포인트를 추출한다")
    void Swift_Vapor_라우팅_API_추출() throws IOException {
        Path file = tempDir.resolve("routes.swift");
        Files.writeString(file, """
                import Vapor

                func routes(_ app: Application) throws {
                    app.get("users") { req in ... }
                    app.post("users") { req in ... }
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Swift");

        assertThat(result.controllerMappings())
                .contains("GET:users", "POST:users");
    }

    // ── B-10 Stage 1: 주석 마스킹 (식별자 검출기가 주석 속 텍스트를 코드로 오인하지 않음) ──

    @Test
    @DisplayName("라인 주석 안의 함수 호출은 functionCalls에 포함되지 않는다 (B-10)")
    void 라인주석_안_호출_제외() throws IOException {
        Path file = writeJavaFile("""
                package com.example;
                public class UserService {
                    public void run() {
                        // legacyValidate(name); 더 이상 사용 안 함
                        validate();
                    }
                    private void validate() {}
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Java");

        assertThat(result.functionCalls().get("run")).contains("validate");
        assertThat(result.functionCalls().getOrDefault("run", List.of())).doesNotContain("legacyValidate");
    }

    @Test
    @DisplayName("블록 주석 안의 함수 호출은 functionCalls에 포함되지 않는다 (B-10)")
    void 블록주석_안_호출_제외() throws IOException {
        Path file = writeJavaFile("""
                package com.example;
                public class UserService {
                    public void run() {
                        /* oldCall();
                           anotherOld(); */
                        validate();
                    }
                    private void validate() {}
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Java");

        assertThat(result.functionCalls().get("run")).contains("validate");
        assertThat(result.functionCalls().getOrDefault("run", List.of()))
                .doesNotContain("oldCall", "anotherOld");
    }

    @Test
    @DisplayName("Python # 주석 안의 함수 호출은 functionCalls에 포함되지 않는다 (B-10)")
    void Python_주석_안_호출_제외() throws IOException {
        Path file = writePyFile("""
                def run():
                    # legacy_call()
                    validate()

                def legacy_call():
                    pass

                def validate():
                    pass
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Python");

        assertThat(result.functionCalls().get("run")).contains("validate");
        assertThat(result.functionCalls().getOrDefault("run", List.of())).doesNotContain("legacy_call");
    }

    @Test
    @DisplayName("문자열 안의 // 는 주석으로 오인되지 않아 같은 줄 뒤 코드가 보존된다 (B-10)")
    void 문자열_안_슬래시_주석_오인_안함() throws IOException {
        Path file = writeJavaFile("""
                package com.example;
                public class UserService {
                    public void run() {
                        log("path // not a comment"); doWork();
                    }
                    private void log(String s) {}
                    private void doWork() {}
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Java");

        assertThat(result.functionCalls().get("run")).contains("log", "doWork");
    }

    @Test
    @DisplayName("주석 안의 new 인스턴스화는 instantiatedClasses에 포함되지 않는다 (B-10)")
    void 주석_안_인스턴스화_제외() throws IOException {
        Path file = writeJavaFile("""
                package com.example;
                public class Factory {
                    public Thing make() {
                        // new LegacyThing();
                        return new RealThing();
                    }
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Java");

        assertThat(result.instantiatedClasses()).contains("RealThing");
        assertThat(result.instantiatedClasses()).doesNotContain("LegacyThing");
    }

    @Test
    @DisplayName("주석 처리된 import는 imports에 포함되지 않는다 (B-10)")
    void 주석_안_import_제외() throws IOException {
        Path file = writeTsFile("""
                // import { Old } from './old';
                import { New } from './new';

                export const f = () => 1;
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "TypeScript");

        assertThat(result.imports()).contains("./new");
        assertThat(result.imports()).doesNotContain("./old");
    }

    @Test
    @DisplayName("파라미터 목록 주석에 ) 가 있어도 함수가 검출된다 (B-10 부수효과: 가려진 함수 회복)")
    void 파라미터_주석_괄호로_가려진_함수_회복() throws IOException {
        // 회귀: 함수 정규식 \([^)]*\) 가 파라미터 주석 속 ')' 에서 끊겨 find 가 미검출되던 케이스.
        // 주석 마스킹 후 비로소 검출 — codeprint DailyMetrics record(주석 "(DAU)")가 실제 사례.
        Path file = writeJavaFile("""
                package com.example;
                public class UserService {
                    public User find(
                        Long id, // 기본 키 (PK)
                        boolean flag) {
                        validate();
                        return null;
                    }
                    private void validate() {}
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Java");

        assertThat(result.functions()).contains("find");
        assertThat(result.functionCalls().get("find")).contains("validate");
    }

    // ── 회귀: 함수 값-참조(콜백) 추출 (B-16) ──────────────────────────────────

    @Test
    @DisplayName("Go에서 함수를 값(콜백)으로 전달 — valueReferencedFunctions에 포함")
    void Go_콜백_값참조_추출() throws IOException {
        // 회귀: gin recovery.go의 CustomRecoveryWithWriter(out, defaultHandleRecovery) —
        // defaultHandleRecovery가 호출(())이 아닌 값으로 전달되어 FUNCTION_CALL 엣지 없음 → DEAD_CODE 오탐
        Path file = writeGoFile("""
                package gin
                func Recovery() HandlerFunc {
                    return CustomRecoveryWithWriter(DefaultWriter, defaultHandleRecovery)
                }
                func defaultHandleRecovery(c *Context, _ any) {
                    c.AbortWithStatus(500)
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Go");

        assertThat(result.valueReferencedFunctions()).contains("defaultHandleRecovery");
    }

    @Test
    @DisplayName("일반 호출(())만 있는 함수 — valueReferencedFunctions에 미포함 (과잉 억제 방지)")
    void 일반_호출은_값참조_아님() throws IOException {
        Path file = writeGoFile("""
                package gin
                func Run() {
                    doWork()
                }
                func doWork() {
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Go");

        // doWork는 호출(())로만 등장 → 값 참조 아님 (호출 엣지로 이미 사용 추적됨)
        assertThat(result.valueReferencedFunctions()).doesNotContain("doWork");
    }

    @Test
    @DisplayName("한정 접근(obj.fn)·자기 정의 — 값 참조로 오인하지 않음")
    void 한정접근과_정의는_값참조_아님() throws IOException {
        Path file = writeJavaFile("""
                package com.example;
                public class Registry {
                    public void register() {
                        router.handle(this.process);
                    }
                    public void handle() {}
                    public void process() {}
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Java");

        // process는 'this.' 뒤 한정 접근 → 다른 객체 멤버일 수 있어 제외. handle은 정의·호출(())뿐 → 제외.
        assertThat(result.valueReferencedFunctions()).doesNotContain("process", "handle", "register");
    }

    @Test
    @DisplayName("Java 콜백 값 전달(stream/메서드 인자) — valueReferencedFunctions에 포함")
    void Java_콜백_값참조_추출() throws IOException {
        Path file = writeJavaFile("""
                package com.example;
                public class Worker {
                    public void start() {
                        scheduler.submit(cleanup);
                    }
                    public void cleanup() {}
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Java");

        // cleanup이 submit(cleanup)에 값으로 전달 — '.'  앞 없음·'(' 뒤 없음
        assertThat(result.valueReferencedFunctions()).contains("cleanup");
    }

    // ── tree-sitter Java 함수·호출 추출 (regex→AST 전환) ────────────────────

    @Test
    @DisplayName("record 타입명을 함수로 오탐하지 않는다 (tree-sitter)")
    void Java_record_타입명_함수_제외() throws IOException {
        // 정규식은 record 선언을 함수 정의로 오인(54건 오탐)했으나 tree-sitter는 record_declaration으로 정확히 구분한다.
        Path file = writeJavaFile("""
                package com.example;
                public class Dtos {
                    public record UserDto(Long id, String name) {}
                    public record Pair(int a, int b) {
                        int sum() { return a + b; }
                    }
                    public void process() {}
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Java");

        assertThat(result.functions()).contains("process", "sum");
        assertThat(result.functions()).doesNotContain("UserDto", "Pair");
    }

    @Test
    @DisplayName("인터페이스 추상 메서드를 함수로 추출한다 (tree-sitter)")
    void Java_인터페이스_추상메서드_추출() throws IOException {
        Path file = writeJavaFile("""
                package com.example;
                public interface UserRepository {
                    User findById(Long id);
                    List<User> searchByUsername(String name);
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Java");

        assertThat(result.functions()).containsExactlyInAnyOrder("findById", "searchByUsername");
    }

    @Test
    @DisplayName("Java 함수 호출을 bare·한정(Class::method) 형식으로 추출한다 (tree-sitter)")
    void Java_함수_호출_추출_treesitter() throws IOException {
        Path file = writeJavaFile("""
                package com.example;
                public class Svc {
                    public void run() {
                        helper();
                        Pattern.compile("x");
                        this.cleanup();
                    }
                    private void helper() {}
                    private void cleanup() {}
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Java");

        assertThat(result.functionCalls().get("run"))
                .contains("helper", "Pattern::compile", "cleanup");
    }

    @Test
    @DisplayName("주석·문자열 리터럴 속 식별자를 호출로 오인하지 않는다 (tree-sitter, B-10 근본 해소)")
    void Java_주석_문자열_식별자_호출_제외() throws IOException {
        // 정규식은 주석/문자열 내부 식별자를 호출로 오인했고 B-10 마스킹으로 우회했으나, AST는 토큰 종류를 구분해 근본 해소한다.
        Path file = writeJavaFile("""
                package com.example;
                public class Svc {
                    public void run() {
                        // fakeMethod() 호출처럼 보이는 주석
                        String sql = "select doStuff() from x";
                        realCall();
                    }
                    private void realCall() {}
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Java");

        assertThat(result.functionCalls().get("run")).contains("realCall");
        assertThat(result.functionCalls().get("run")).doesNotContain("fakeMethod", "doStuff");
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

    private Path writeGoFile(String content) throws IOException {
        Path file = tempDir.resolve("test_file.go");
        Files.writeString(file, content);
        return file;
    }
}
