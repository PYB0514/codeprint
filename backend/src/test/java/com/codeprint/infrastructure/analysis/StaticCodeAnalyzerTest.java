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
