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
    @DisplayName("TypeScript 클래스 메서드(데코레이터 없이 methodName() {} 형태)를 함수로 추출한다")
    void TypeScript_클래스_메서드_함수_추출() throws IOException {
        Path file = writeTsFile("""
                export class CatsService {
                    async findAll(): Promise<Cat[]> {
                        return [];
                    }
                    create(dto: CreateCatDto) {
                        return dto;
                    }
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "TypeScript");

        assertThat(result.functions()).contains("findAll", "create");
    }

    @Test
    @DisplayName("TypeScript 클래스 메서드 추출 시 제어문 키워드는 함수로 오인하지 않는다")
    void TypeScript_클래스_메서드_제어문_오탐_방지() throws IOException {
        Path file = writeTsFile("""
                export class OrderService {
                    process(order: Order) {
                        if (order.isValid()) {
                            for (const item of order.items) {
                                while (item.retryCount > 0) {}
                            }
                        }
                        try {
                            this.save(order);
                        } catch (e) {}
                        return order;
                    }
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "TypeScript");

        assertThat(result.functions()).contains("process");
        assertThat(result.functions()).doesNotContain("if", "for", "while", "try", "catch");
    }

    @Test
    @DisplayName("useCallback으로 감싼 함수를 함수 정의로 추출한다")
    void TypeScript_useCallback_감싼_함수_추출() throws IOException {
        // task_4190b92e: variable_declarator의 value가 call_expression(useCallback)이라 기존엔 함수로 인식 안 됨
        Path file = writeTsFile("""
                function Foo() {
                    const plainArrow = () => { doWork(); };
                    const handleClick = useCallback(() => {
                        doWork();
                    }, []);
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "TypeScript");

        assertThat(result.functions()).contains("plainArrow", "handleClick");
        assertThat(result.functionCalls().get("handleClick")).contains("doWork");
    }

    @Test
    @DisplayName("memo·forwardRef로 감싼 컴포넌트를 함수 정의로 추출한다")
    void TypeScript_memo_forwardRef_감싼_함수_추출() throws IOException {
        Path file = writeTsFile("""
                const Panel = memo(() => {
                    renderPanel();
                });
                const Input = forwardRef((props, ref) => {
                    renderInput();
                });
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "TypeScript");

        assertThat(result.functions()).contains("Panel", "Input");
        assertThat(result.functionCalls().get("Panel")).contains("renderPanel");
        assertThat(result.functionCalls().get("Input")).contains("renderInput");
    }

    @Test
    @DisplayName("useMemo로 감싼 값은 함수가 아닐 수 있어 함수 정의로 승격하지 않는다")
    void TypeScript_useMemo_감싼_값은_함수_미추출() throws IOException {
        // precision 우선: useMemo는 반환값이 함수가 아닌 경우가 많아 허용목록에서 제외(PROGRESS.md 조사 결론)
        Path file = writeTsFile("""
                function Foo() {
                    const total = useMemo(() => computeTotal(), []);
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "TypeScript");

        assertThat(result.functions()).doesNotContain("total");
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

    // ── 타입 인지 호출 해소 (Phase 2) ────────────────────────────────────────

    @Test
    @DisplayName("주입 필드 수신자 호출을 선언 타입으로 한정한다(repo.save → AnalysisRepository::save)")
    void 필드_수신자_타입_해소() throws IOException {
        Path file = writeJavaFile("""
                package com.example;
                public class AnalysisService {
                    private final AnalysisRepository repo;
                    public AnalysisService(AnalysisRepository repo) { this.repo = repo; }
                    public void run() {
                        repo.save(null);
                    }
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Java");

        assertThat(result.functionCalls().get("run")).contains("AnalysisRepository::save");
        assertThat(result.functionCalls().get("run")).doesNotContain("save");
    }

    @Test
    @DisplayName("this.field 수신자 호출도 필드 선언 타입으로 한정한다")
    void this_필드_수신자_타입_해소() throws IOException {
        Path file = writeJavaFile("""
                package com.example;
                public class AnalysisService {
                    private final AnalysisRepository repo;
                    public void run() {
                        this.repo.save(null);
                    }
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Java");

        assertThat(result.functionCalls().get("run")).contains("AnalysisRepository::save");
    }

    @Test
    @DisplayName("파라미터·지역변수 수신자 호출도 선언 타입으로 한정한다")
    void 파라미터_지역변수_수신자_타입_해소() throws IOException {
        Path file = writeJavaFile("""
                package com.example;
                public class GraphService {
                    public void handle(GraphRepository graphRepo) {
                        graphRepo.findById(null);
                        Project project = load();
                        project.getName();
                    }
                    private Project load() { return null; }
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Java");

        assertThat(result.functionCalls().get("handle"))
                .contains("GraphRepository::findById", "Project::getName");
    }

    @Test
    @DisplayName("선언 타입을 모르는 수신자는 bare name으로 유지한다(폴백 recall 보존)")
    void 미해소_수신자는_bare_유지() throws IOException {
        // helper()는 수신자 없는 자기 메서드 호출, unknownVar는 선언이 없어 타입 미상
        Path file = writeJavaFile("""
                package com.example;
                public class FooService {
                    public void run() {
                        helper();
                    }
                    private void helper() {}
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Java");

        assertThat(result.functionCalls().get("run")).contains("helper");
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
    @DisplayName("Go 리시버 변수 수신자 호출을 선언 타입으로 한정한다(c *Context → c.reset()=Context::reset)")
    void Go_리시버_수신자_타입_해소() throws IOException {
        Path file = tempDir.resolve("context.go");
        Files.writeString(file, """
                package gin

                type Context struct {}

                func (c *Context) Next() {
                    c.reset()
                }

                func (c *Context) reset() {}
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Go");

        assertThat(result.functionCalls().get("Next")).contains("Context::reset");
        assertThat(result.functionCalls().get("Next")).doesNotContain("reset");
        // 파일이 선언한 타입명이 declaredTypes로 방출돼야 Type::method 해소가 가능
        assertThat(result.declaredTypes()).contains("Context");
    }

    @Test
    @DisplayName("Go 파라미터 타입 수신자도 선언 타입으로 한정한다(s *Server → s.Handle())")
    void Go_파라미터_수신자_타입_해소() throws IOException {
        Path file = tempDir.resolve("router.go");
        Files.writeString(file, """
                package gin

                func dispatch(s *Server) {
                    s.Handle()
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Go");

        assertThat(result.functionCalls().get("dispatch")).contains("Server::Handle");
    }

    @Test
    @DisplayName("Go 패키지 함수 호출은 bare로 유지된다(fmt.Println → Println, 스코프에 없는 수신자)")
    void Go_패키지_호출_bare_유지() throws IOException {
        Path file = tempDir.resolve("log.go");
        Files.writeString(file, """
                package gin

                func report() {
                    fmt.Println("x")
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Go");

        // fmt는 스코프에 없는 패키지명 → bare 유지 (로컬 함수명 매칭 보존)
        assertThat(result.functionCalls().get("report")).contains("Println");
        assertThat(result.functionCalls().get("report")).doesNotContain("fmt::Println");
    }

    @Test
    @DisplayName("Go 지역변수 복합 리터럴 타입 수신자를 한정한다(e := Engine{} → e.Run())")
    void Go_지역변수_복합리터럴_타입_해소() throws IOException {
        Path file = tempDir.resolve("main.go");
        Files.writeString(file, """
                package main

                func boot() {
                    e := Engine{}
                    e.Run()
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Go");

        assertThat(result.functionCalls().get("boot")).contains("Engine::Run");
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
    @DisplayName("C# 주입 필드 수신자 호출을 선언 타입으로 한정한다(_repo.Save → AnalysisRepository::Save)")
    void CSharp_필드_수신자_타입_해소() throws IOException {
        Path file = tempDir.resolve("OrderService.cs");
        Files.writeString(file, """
                public class OrderService {
                    private readonly AnalysisRepository _repo;
                    public OrderService(AnalysisRepository repo) { _repo = repo; }
                    public void Run() {
                        _repo.Save(null);
                    }
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "C#");

        assertThat(result.functionCalls().get("Run")).contains("AnalysisRepository::Save");
        assertThat(result.functionCalls().get("Run")).doesNotContain("Save");
    }

    @Test
    @DisplayName("C# 제네릭 필드 타입은 베이스명으로 한정한다(_repo:IRepository<Order> → IRepository::GetById)")
    void CSharp_제네릭_필드_베이스명_해소() throws IOException {
        Path file = tempDir.resolve("Handler.cs");
        Files.writeString(file, """
                public class Handler {
                    private readonly IRepository<Order> _repo;
                    public void Handle(IMediator mediator) {
                        _repo.GetById(1);
                        mediator.Send(null);
                        Order order = Load();
                        order.Confirm();
                    }
                    private Order Load() { return null; }
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "C#");

        assertThat(result.functionCalls().get("Handle"))
                .contains("IRepository::GetById", "IMediator::Send", "Order::Confirm");
    }

    @Test
    @DisplayName("C# 12 primary constructor 파라미터 수신자도 선언 타입으로 한정한다")
    void CSharp_primary_constructor_수신자_타입_해소() throws IOException {
        // 현대 C#(clean-architecture) DI 핵심 패턴 — 필드가 아니라 클래스 헤더 파라미터로 주입
        Path file = tempDir.resolve("DeleteService.cs");
        Files.writeString(file, """
                public class DeleteService(IRepository<Contributor> _repository, IMediator _mediator) : IDeleteService {
                    public async Task Delete(Guid id) {
                        await _repository.GetByIdAsync(id);
                        await _mediator.Publish(null);
                    }
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "C#");

        assertThat(result.functionCalls().get("Delete"))
                .contains("IRepository::GetByIdAsync", "IMediator::Publish");
        assertThat(result.functionCalls().get("Delete")).doesNotContain("GetByIdAsync", "Publish");
    }

    @Test
    @DisplayName("C# nullable 필드 타입(Foo?)은 언래핑해 한정한다")
    void CSharp_nullable_필드_타입_해소() throws IOException {
        Path file = tempDir.resolve("Holder.cs");
        Files.writeString(file, """
                public class Holder {
                    private Contributor? _c;
                    public void Run() {
                        _c.UpdateName(null);
                    }
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "C#");

        assertThat(result.functionCalls().get("Run")).contains("Contributor::UpdateName");
        assertThat(result.functionCalls().get("Run")).doesNotContain("UpdateName");
    }

    @Test
    @DisplayName("C# var(추론) 수신자는 타입을 모르므로 bare 유지")
    void CSharp_var_수신자_bare_유지() throws IOException {
        Path file = tempDir.resolve("VarService.cs");
        Files.writeString(file, """
                public class VarService {
                    public void Run() {
                        var x = Build();
                        x.DoThing();
                    }
                    private object Build() { return null; }
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "C#");

        assertThat(result.functionCalls().get("Run")).contains("DoThing");
        assertThat(result.functionCalls().get("Run")).noneMatch(c -> c.endsWith("::DoThing"));
    }

    @Test
    @DisplayName("TS 생성자 파라미터 프로퍼티 this.field 호출을 선언 타입으로 한정한다(NestJS DI)")
    void TS_생성자_파라미터_프로퍼티_타입_해소() throws IOException {
        Path file = tempDir.resolve("UserService.ts");
        Files.writeString(file, """
                class UserService {
                  constructor(
                    private readonly userRepository: Repository<User>,
                    private userService: UserService
                  ) {}
                  async update(id: number) {
                    await this.userRepository.findOne(id);
                    this.userService.findById(id);
                  }
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "TypeScript");

        assertThat(result.functionCalls().get("update"))
                .contains("Repository::findOne", "UserService::findById");
        assertThat(result.functionCalls().get("update")).doesNotContain("findOne", "findById");
    }

    @Test
    @DisplayName("TS 파라미터·지역변수(어노테이션·new) 수신자 호출을 타입으로 한정한다")
    void TS_파라미터_지역변수_타입_해소() throws IOException {
        Path file = tempDir.resolve("Handler.ts");
        Files.writeString(file, """
                class Handler {
                  run(article: Article) {
                    article.publish();
                    const c: Comment = build();
                    c.flag();
                    const d = new Draft();
                    d.save();
                  }
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "TypeScript");

        assertThat(result.functionCalls().get("run"))
                .contains("Article::publish", "Comment::flag", "Draft::save");
    }

    @Test
    @DisplayName("TS 클래스/인터페이스 선언명을 declaredTypes로 추출한다(파일명≠클래스명 해소용)")
    void TS_declaredTypes_추출() throws IOException {
        Path file = tempDir.resolve("article.service.ts");
        Files.writeString(file, """
                export interface IArticle {}
                export class ArticleService {
                  findAll() {}
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "TypeScript");

        assertThat(result.declaredTypes()).contains("ArticleService", "IArticle");
    }

    @Test
    @DisplayName("TS 타입을 모르는 수신자(어노테이션·new 없음)는 bare 유지")
    void TS_미해소_수신자_bare_유지() throws IOException {
        Path file = tempDir.resolve("Svc.ts");
        Files.writeString(file, """
                class Svc {
                  run() {
                    const x = build();
                    x.doThing();
                  }
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "TypeScript");

        assertThat(result.functionCalls().get("run")).contains("doThing");
        assertThat(result.functionCalls().get("run")).noneMatch(c -> c.endsWith("::doThing"));
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

    // ── 서비스 간 호출(serviceCalls, 모노레포 MSA) ──────────────────────────

    @Test
    @DisplayName("WebClient.uri()의 http:// 호스트에서 대상 서비스 논리명을 추출한다")
    void serviceCalls_webClient_추출() throws IOException {
        Path file = writeJavaFile("""
                package com.example.api;
                public class CustomersServiceClient {
                    public Mono<OwnerDetails> getOwner(int id) {
                        return webClientBuilder.build().get()
                            .uri("http://customers-service/owners/{id}", id)
                            .retrieve()
                            .bodyToMono(OwnerDetails.class);
                    }
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Java");

        assertThat(result.serviceCalls()).contains("customers-service");
    }

    @Test
    @DisplayName("RestTemplate.getForObject()의 http:// 호스트에서 대상 서비스 논리명을 추출한다")
    void serviceCalls_restTemplate_추출() throws IOException {
        Path file = writeJavaFile("""
                package com.example.api;
                public class OrderClient {
                    public Order getOrder(String id) {
                        return restTemplate.getForObject("http://order-service/orders/" + id, Order.class);
                    }
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Java");

        assertThat(result.serviceCalls()).contains("order-service");
    }

    @Test
    @DisplayName("로컬 경로(http:// 없음) 호출은 serviceCalls로 추출하지 않는다")
    void serviceCalls_로컬_경로_제외() throws IOException {
        Path file = writeJavaFile("""
                package com.example.api;
                public class LocalClient {
                    public void call() {
                        webClientBuilder.build().get().uri("/local/path").retrieve();
                    }
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Java");

        assertThat(result.serviceCalls()).isEmpty();
    }

    @Test
    @DisplayName("TS/JS의 fetch() 호출(axios 아님)은 serviceCalls로 추출하지 않는다")
    void serviceCalls_fetch_스코프밖() throws IOException {
        Path file = writeTsFile("""
                export const call = () => fetch('http://customers-service/owners/1');
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "TypeScript");

        assertThat(result.serviceCalls()).isEmpty();
    }

    @Test
    @DisplayName("axios.get()의 http:// 호스트에서 대상 서비스 논리명을 추출한다(TypeScript)")
    void serviceCalls_axios_추출() throws IOException {
        Path file = writeTsFile("""
                export const getOwner = (id: number) =>
                    axios.get(`http://customers-service/owners/${id}`);
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "TypeScript");

        assertThat(result.serviceCalls()).contains("customers-service");
    }

    @Test
    @DisplayName("requests.get()의 http:// 호스트에서 대상 서비스 논리명을 추출한다(Python)")
    void serviceCalls_python_requests_추출() throws IOException {
        Path file = writePyFile("""
                import requests

                def get_owner(owner_id):
                    return requests.get(f"http://customers-service/owners/{owner_id}")
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Python");

        assertThat(result.serviceCalls()).contains("customers-service");
    }

    @Test
    @DisplayName("로컬 경로(http:// 없음) requests 호출은 serviceCalls로 추출하지 않는다(Python)")
    void serviceCalls_python_로컬_경로_제외() throws IOException {
        Path file = writePyFile("""
                import requests

                def call():
                    requests.get("/local/path")
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Python");

        assertThat(result.serviceCalls()).isEmpty();
    }

    // ── FeignClient 서비스 대상(feignClientTarget, SERVICE_CALL_CHAIN 확장) ──────────

    @Test
    @DisplayName("@FeignClient(name=...)에서 논리 서비스명을 추출한다")
    void feignClientTarget_name속성_추출() throws IOException {
        Path file = writeJavaFile("""
                package com.example.api;
                @FeignClient(name = "customers-service")
                public interface CustomersServiceClient {
                    @GetMapping("/owners/{id}")
                    OwnerDetails getOwner(@PathVariable int id);
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Java");

        assertThat(result.feignClientTarget()).isEqualTo("customers-service");
    }

    @Test
    @DisplayName("@FeignClient(\"...\")처럼 위치 인자만 있어도 논리 서비스명을 추출한다")
    void feignClientTarget_바레인자_추출() throws IOException {
        Path file = writeJavaFile("""
                package com.example.api;
                @FeignClient("visits-service")
                public interface VisitsServiceClient {
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Java");

        assertThat(result.feignClientTarget()).isEqualTo("visits-service");
    }

    @Test
    @DisplayName("@FeignClient가 없으면 feignClientTarget은 null이다")
    void feignClientTarget_미선언_null() throws IOException {
        Path file = writeJavaFile("""
                package com.example.api;
                public interface PlainInterface {
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Java");

        assertThat(result.feignClientTarget()).isNull();
    }

    // ── Spring 빈 스테레오타입·필드 의존·@Lazy(CIRCULAR_BEAN_DEPENDENCY) ──────

    @Test
    @DisplayName("@Service 어노테이션이 붙은 클래스는 beanStereotype이 Service다")
    void beanStereotype_Service_추출() throws IOException {
        Path file = writeJavaFile("""
                package com.example;
                @Service
                public class OrderService {
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Java");

        assertThat(result.beanStereotype()).isEqualTo("Service");
    }

    @Test
    @DisplayName("Spring 빈 스테레오타입 어노테이션이 없으면 beanStereotype은 null이다")
    void beanStereotype_미선언_null() throws IOException {
        Path file = writeJavaFile("""
                package com.example;
                public class PlainClass {
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Java");

        assertThat(result.beanStereotype()).isNull();
    }

    @Test
    @DisplayName("필드 선언 타입명을 fieldDependencyTypes로 추출한다(distinct)")
    void fieldDependencyTypes_필드타입_추출() throws IOException {
        Path file = writeJavaFile("""
                package com.example;
                @Service
                public class OrderService {
                    private final PaymentService paymentService;
                    private final PaymentService duplicate;
                    private final InventoryService inventoryService;
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Java");

        assertThat(result.fieldDependencyTypes()).containsExactlyInAnyOrder("PaymentService", "InventoryService");
    }

    @Test
    @DisplayName("생성자 파라미터의 @Lazy 어노테이션이 붙은 타입명을 lazyDependencyTypes로 추출한다")
    void lazyDependencyTypes_Lazy파라미터_추출() throws IOException {
        Path file = writeJavaFile("""
                package com.example;
                @Service
                public class OrderService {
                    private final PaymentService paymentService;
                    private final InventoryService inventoryService;
                    public OrderService(@Lazy PaymentService paymentService, InventoryService inventoryService) {
                        this.paymentService = paymentService;
                        this.inventoryService = inventoryService;
                    }
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Java");

        assertThat(result.lazyDependencyTypes()).containsExactly("PaymentService");
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

    @Test
    @DisplayName("Java 컨트롤러 매핑마다 어노테이션 다음에 오는 메서드를 처리 함수로 해소한다")
    void Java_컨트롤러_매핑_처리함수_해소() throws IOException {
        Path file = writeJavaFile("""
                package com.example;
                @RestController
                @RequestMapping("/api/projects")
                public class ProjectController {
                    @GetMapping("/list")
                    public List<Project> list() { return null; }
                    @PostMapping
                    public Project create() { return null; }
                    @DeleteMapping("/{id}")
                    public void delete() {}
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Java");

        assertThat(result.controllerMappingFunctions())
                .containsEntry("/api/projects/list", "list")
                .containsEntry("/api/projects", "create")
                .containsEntry("/api/projects/{id}", "delete");
    }

    @Test
    @DisplayName("같은 경로에 GET/POST가 겹치면 나중에 매칭된 함수로 덮어써진다 (알려진 한계)")
    void Java_컨트롤러_매핑_동일경로_후행함수로_덮어쓰기() throws IOException {
        Path file = writeJavaFile("""
                package com.example;
                @RestController
                @RequestMapping("/api/projects")
                public class ProjectController {
                    @GetMapping
                    public List<Project> list() { return null; }
                    @PostMapping
                    public Project create() { return null; }
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Java");

        assertThat(result.controllerMappingFunctions()).containsEntry("/api/projects", "create");
    }

    @Test
    @DisplayName("Express 컨트롤러 매핑마다 마지막 인자가 순수 식별자면 처리 함수로 해소한다")
    void Express_컨트롤러_매핑_처리함수_해소() throws IOException {
        Path file = tempDir.resolve("userRouter.js");
        Files.writeString(file, """
                const express = require('express');
                const router = express.Router();
                function getUsers(req, res) {}
                function createUser(req, res) {}
                router.get('/users', getUsers);
                router.post('/users', authMiddleware, createUser);
                router.delete('/users/:id', (req, res) => { res.send(); });
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "JavaScript");

        assertThat(result.controllerMappingFunctions())
                .containsEntry("GET:/users", "getUsers")
                .containsEntry("POST:/users", "createUser")
                .doesNotContainKey("DELETE:/users/:id");
    }

    @Test
    @DisplayName("NestJS 컨트롤러 매핑마다 클래스 메서드를 처리 함수로 해소한다")
    void NestJS_컨트롤러_매핑_처리함수_해소() throws IOException {
        Path file = tempDir.resolve("cats.controller.ts");
        Files.writeString(file, """
                import { Controller, Get, Post } from '@nestjs/common';

                @Controller('cats')
                export class CatsController {
                    @Get()
                    findAll() {}

                    @Post()
                    create() {}
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "TypeScript");

        assertThat(result.controllerMappingFunctions())
                .containsEntry("GET:/cats", "findAll")
                .containsEntry("POST:/cats", "create");
    }

    @Test
    @DisplayName("FastAPI 컨트롤러 매핑마다 데코레이터 다음에 오는 함수를 처리 함수로 해소한다")
    void FastAPI_컨트롤러_매핑_처리함수_해소() throws IOException {
        Path file = writePyFile("""
                from fastapi import FastAPI
                app = FastAPI()

                @app.get("/items")
                async def get_items():
                    pass

                @app.post("/items")
                async def create_item():
                    pass
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Python");

        assertThat(result.controllerMappingFunctions())
                .containsEntry("GET:/items", "get_items")
                .containsEntry("POST:/items", "create_item");
    }

    @Test
    @DisplayName("Django path()의 뷰 참조에서 마지막 식별자를 처리 함수로 해소한다")
    void Django_컨트롤러_매핑_처리함수_해소() throws IOException {
        Path file = writePyFile("""
                from django.urls import path
                from . import views

                urlpatterns = [
                    path('items/', views.item_list),
                    path('items/<int:pk>/', views.item_detail),
                ]
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Python");

        assertThat(result.controllerMappingFunctions())
                .containsEntry("GET:/items/", "item_list")
                .containsEntry("GET:/items/<int:pk>/", "item_detail");
    }

    @Test
    @DisplayName("Go Gin 컨트롤러 매핑마다 마지막 인자가 순수 식별자면 처리 함수로 해소한다")
    void GoGin_컨트롤러_매핑_처리함수_해소() throws IOException {
        Path file = tempDir.resolve("main.go");
        Files.writeString(file, """
                package main
                import "github.com/gin-gonic/gin"
                func getUsers(c *gin.Context) {}
                func main() {
                    r := gin.Default()
                    r.GET("/users", getUsers)
                    r.POST("/users", func(c *gin.Context) {})
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Go");

        assertThat(result.controllerMappingFunctions())
                .containsEntry("GET:/users", "getUsers")
                .doesNotContainKey("POST:/users");
    }

    @Test
    @DisplayName("Go 리시버 메서드 참조(h.GetUsers)는 마지막 세그먼트만 처리 함수로 해소한다")
    void GoGin_리시버_메서드_처리함수_해소() throws IOException {
        Path file = tempDir.resolve("router.go");
        Files.writeString(file, """
                package main
                func main() {
                    r.GET("/users", h.GetUsers)
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Go");

        assertThat(result.controllerMappingFunctions()).containsEntry("GET:/users", "GetUsers");
    }

    @Test
    @DisplayName("Ruby는 이번 스코프에서 제외 — 처리 함수가 해소되지 않는다 (route가 항상 다른 파일의 컨트롤러#액션을 문자열로 참조)")
    void Ruby_컨트롤러_매핑_처리함수_미해소() throws IOException {
        Path file = tempDir.resolve("routes.rb");
        Files.writeString(file, """
                Rails.application.routes.draw do
                    get '/posts', to: 'posts#index'
                end
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Ruby");

        assertThat(result.controllerMappingFunctions()).isEmpty();
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
    @DisplayName("C# 메서드·생성자·로컬함수·표현식바디를 모두 추출한다 (정규식이 놓치던 형태)")
    void CSharp_AST_확장형_함수_추출() throws IOException {
        Path file = tempDir.resolve("Service.cs");
        Files.writeString(file, """
                public class Service {
                    public Service() {}
                    internal void Configure() {}
                    public override string ToText() => "x";
                    public void Run() {
                        int Local() => 1;
                        Local();
                    }
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "C#");

        // 생성자(Service)·internal·override·표현식바디(=>)·로컬함수(Local)까지 — 정규식은 modifier/형태 제약으로 일부를 놓쳤다.
        assertThat(result.functions())
                .containsExactlyInAnyOrder("Service", "Configure", "ToText", "Run", "Local");
    }

    @Test
    @DisplayName("C# 호출을 bare·Type::method·this 형태로 귀속한다")
    void CSharp_함수_호출_추출() throws IOException {
        Path file = tempDir.resolve("Worker.cs");
        Files.writeString(file, """
                public class Worker {
                    public void Run() {
                        Process();
                        this.Process();
                        Console.WriteLine("hi");
                    }
                    private void Process() {}
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "C#");

        assertThat(result.functionCalls()).containsKey("Run");
        // bare Process()·this.Process() → "Process", 대문자 수신자 Console.WriteLine → "Console::WriteLine"
        assertThat(result.functionCalls().get("Run")).contains("Process", "Console::WriteLine");
    }

    @Test
    @DisplayName("UTF-8 BOM이 붙은 C# 파일도 식별자를 정확히 추출한다 (BOM 오프셋 회귀)")
    void CSharp_BOM_파일_식별자_정확추출() throws IOException {
        Path file = tempDir.resolve("Bom.cs");
        // .NET 소스는 UTF-8 BOM 저장이 흔하다. BOM(3바이트)이 tree-sitter 오프셋과 어긋나면 모든 이름이 밀려 깨진다.
        Files.writeString(file, "\uFEFF" + """
                public class Bom {
                    public void Compute() {
                        Helper();
                    }
                    private void Helper() {}
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "C#");

        assertThat(result.functions()).containsExactlyInAnyOrder("Compute", "Helper");
        assertThat(result.functionCalls().get("Compute")).contains("Helper");
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
    @DisplayName("Ruby 싱글톤 메서드(def self.x)는 실제 메서드명을 추출한다 (정규식의 self 오캡처를 AST가 교정)")
    void Ruby_싱글톤_메서드_추출() throws IOException {
        Path file = tempDir.resolve("factory.rb");
        Files.writeString(file, """
                class Factory
                  def self.build(name)
                    create(name)
                  end

                  def create(name)
                    name
                  end
                end
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Ruby");

        // 정규식 `def\\s+(\\w+)`은 `def self.build`에서 `self`를 캡처했으나, AST는 singleton_method name 필드로 `build`를 정확히 추출한다.
        assertThat(result.functions()).containsExactlyInAnyOrder("build", "create");
        assertThat(result.functions()).doesNotContain("self");
    }

    @Test
    @DisplayName("Ruby 호출을 bare·괄호없는 호출·Constant::method 형태로 귀속한다")
    void Ruby_함수_호출_추출() throws IOException {
        Path file = tempDir.resolve("worker.rb");
        Files.writeString(file, """
                class Worker
                  def run
                    process(1)
                    save name
                    Logger.info("hi")
                  end

                  def process(x)
                  end
                end
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Ruby");

        assertThat(result.functionCalls()).containsKey("run");
        // process(1)·save name(괄호 없는 명령형 호출) → bare, Logger.info(상수 수신자) → "Logger::info"
        assertThat(result.functionCalls().get("run")).contains("process", "save", "Logger::info");
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
    @DisplayName("PHP 최상위 function과 클래스 method를 함께 추출한다")
    void PHP_최상위_함수_추출() throws IOException {
        Path file = tempDir.resolve("helpers.php");
        Files.writeString(file, """
                <?php
                function array_get($arr, $key) {
                    return $arr[$key];
                }
                class Box {
                    public function open() {}
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "PHP");

        assertThat(result.functions()).containsExactlyInAnyOrder("array_get", "open");
    }

    @Test
    @DisplayName("PHP 호출을 bare·->·?->·Class::method 로 귀속하고 키워드는 호출로 세지 않는다")
    void PHP_함수_호출_추출() throws IOException {
        Path file = tempDir.resolve("Worker.php");
        Files.writeString(file, """
                <?php
                class Worker {
                    public function run($items) {
                        helper($items);
                        $this->save($items);
                        $repo?->find($items);
                        Logger::info("hi");
                        foreach ($items as $i) {
                            process($i);
                        }
                    }
                    private function save($items) {}
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "PHP");

        assertThat(result.functionCalls()).containsKey("run");
        // helper()·process() bare, $this->save() bare, $repo?->find() nullsafe bare, Logger::info() 정적 → "Logger::info"
        assertThat(result.functionCalls().get("run")).contains("helper", "save", "find", "Logger::info", "process");
        // 정규식이 호출로 오인하던 PHP 키워드(foreach 등)는 AST가 호출로 세지 않는다
        assertThat(result.functionCalls().get("run")).doesNotContain("foreach");
    }

    @Test
    @DisplayName("C 함수 정의를 선언자 체인에서 추출한다 (포인터 반환·static 포함, 함수 포인터 선언은 제외)")
    void C_함수_추출() throws IOException {
        Path file = tempDir.resolve("util.c");
        Files.writeString(file, """
                #include <stdio.h>

                static int helper(int x) { return x + 1; }

                char *make_buffer(size_t n) { return malloc(n); }

                void (*g_callback)(int);

                int main(void) { return 0; }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "C");

        // helper(static)·make_buffer(포인터 반환 → pointer_declarator 한 겹)·main 추출, g_callback(함수 포인터 선언)은 제외
        assertThat(result.functions()).containsExactlyInAnyOrder("helper", "make_buffer", "main");
    }

    @Test
    @DisplayName("C 호출을 bare 식별자로 함수에 귀속한다")
    void C_함수_호출_추출() throws IOException {
        Path file = tempDir.resolve("worker.c");
        Files.writeString(file, """
                int helper(int x) { return x; }

                int run(int n) {
                    int r = helper(n);
                    log_result(r);
                    return r;
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "C");

        assertThat(result.functionCalls()).containsKey("run");
        assertThat(result.functionCalls().get("run")).contains("helper", "log_result");
    }

    @Test
    @DisplayName("C++ 클래스 메서드·생성자·소멸자·연산자·아웃오브라인 정의를 추출한다")
    void Cpp_함수_추출() throws IOException {
        Path file = tempDir.resolve("Widget.cpp");
        Files.writeString(file, """
                namespace ui {
                class Widget {
                public:
                    Widget() {}
                    ~Widget() {}
                    int area() const { return w * h; }
                    Widget& operator+(const Widget& o) { return *this; }
                    static Widget make() { return Widget(); }
                    virtual void render() = 0;
                private:
                    int w, h;
                };
                void Widget::render() {}
                template<typename T>
                T identity(T v) { return v; }
                int* allocate(int n) { return new int[n]; }
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "C++");

        // 생성자(Widget)·소멸자(~Widget)·인라인 메서드(area)·연산자(operator+)·static(make)·아웃오브라인(render: Widget::render → bare render)
        // ·템플릿(identity)·포인터 반환(allocate). 순수 가상 선언(render() = 0;)은 field_declaration이라 정의 본문이 없어 제외되나
        // 같은 이름의 아웃오브라인 정의가 render를 채운다. 함수 포인터 반환은 pointer_declarator 한 겹.
        assertThat(result.functions())
                .contains("Widget", "~Widget", "area", "operator+", "make", "render", "identity", "allocate");
    }

    @Test
    @DisplayName("C++ 호출을 bare·멤버·qualified 규약으로 함수에 귀속한다")
    void Cpp_함수_호출_추출() throws IOException {
        Path file = tempDir.resolve("service.cpp");
        Files.writeString(file, """
                int helper(int x) { return x; }
                void run() {
                    helper(1);
                    obj.method();
                    ptr->doWork();
                    Logger::info("x");
                    ns::freeFunc();
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "C++");

        assertThat(result.functionCalls()).containsKey("run");
        // helper bare, obj.method()→bare method(소문자 수신자), ptr->doWork()→bare doWork, Logger::info()→"Logger::info"(대문자 scope),
        // ns::freeFunc()→bare freeFunc(소문자 namespace scope)
        assertThat(result.functionCalls().get("run"))
                .contains("helper", "method", "doWork", "Logger::info", "freeFunc");
    }

    @Test
    @DisplayName("C++ 매크로 에러 복구 시 키워드(namespace)가 함수명으로 누출되지 않는다")
    void Cpp_키워드_누출_차단() throws IOException {
        // 불투명 매크로 뒤 `namespace X {`는 tree-sitter가 function_definition으로 오파싱(declarator=identifier "namespace").
        // 키워드 가드가 이를 차단하고, 안에 중첩된 진짜 함수는 정상 추출돼야 한다(nlohmann/json 류 패턴).
        Path file = tempDir.resolve("detail.cpp");
        Files.writeString(file, """
                #define NS_BEGIN namespace lib {
                NS_BEGIN
                namespace detail {
                void real_func() { helper(); }
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "C++");

        assertThat(result.functions()).contains("real_func");
        assertThat(result.functions()).doesNotContain("namespace", "detail");
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

    // ── 타입 인지 호출 해소 (Phase 2, Python) ────────────────────────────────

    @Test
    @DisplayName("Python self.attr=ClassName() 생성자 대입 수신자를 타입으로 한정한다(FastAPI repo 패턴)")
    void Python_self_생성자_대입_타입_해소() throws IOException {
        Path file = writePyFile("""
                class ArticlesRepository:
                    def __init__(self, conn):
                        self._profiles_repo = ProfilesRepository(conn)

                    async def get(self, slug):
                        return await self._profiles_repo.get_profile_by_username(slug)
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Python");

        assertThat(result.functionCalls().get("get")).contains("ProfilesRepository::get_profile_by_username");
        assertThat(result.functionCalls().get("get")).doesNotContain("get_profile_by_username");
    }

    @Test
    @DisplayName("Python self.attr:Type 어노테이션·타입힌트 파라미터·지역변수 생성자 수신자를 타입으로 한정한다")
    void Python_어노테이션_파라미터_지역변수_타입_해소() throws IOException {
        Path file = writePyFile("""
                class Service:
                    def __init__(self):
                        self._repo: UserRepository = build()

                    def run(self, profile: Profile):
                        self._repo.find_one()
                        profile.follow()
                        tags = TagsRepository(self.conn)
                        tags.create_tag()
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Python");

        assertThat(result.functionCalls().get("run"))
                .contains("UserRepository::find_one", "Profile::follow", "TagsRepository::create_tag");
    }

    @Test
    @DisplayName("Python 클래스 선언명을 declaredTypes로 추출한다(파일명≠클래스명 해소용)")
    void Python_declaredTypes_추출() throws IOException {
        Path file = tempDir.resolve("profiles.py");
        Files.writeString(file, """
                class ProfilesRepository:
                    def get_profile_by_username(self, name):
                        pass
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Python");

        assertThat(result.declaredTypes()).contains("ProfilesRepository");
    }

    @Test
    @DisplayName("Python 타입을 모르는 self.attr(어노테이션·생성자 대입 없음) 수신자는 bare 유지")
    void Python_미해소_수신자_bare_유지() throws IOException {
        Path file = writePyFile("""
                class Svc:
                    def __init__(self, dep):
                        self._dep = dep

                    def run(self):
                        self._dep.do_thing()
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Python");

        assertThat(result.functionCalls().get("run")).contains("do_thing");
        assertThat(result.functionCalls().get("run")).noneMatch(c -> c.endsWith("::do_thing"));
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
    @DisplayName("Django 프로젝트 추상 베이스 상속 모델(class Article(TimestampedModel)) — 필드 신호로 감지")
    void Django_추상베이스_상속모델_감지() throws IOException {
        // 실전 Django는 models.Model 직접 상속이 드물고 프로젝트 공통 추상 베이스를 상속한다. 본문 models.*Field 로 감지.
        Path file = writePyFile("""
                from django.db import models
                from conduit.apps.core.models import TimestampedModel

                class Article(TimestampedModel):
                    title = models.CharField(max_length=200)
                    author = models.ForeignKey('Profile', on_delete=models.CASCADE)
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Python");

        assertThat(result.dbTables()).extracting(DbTableInfo::tableName)
                .containsExactly("article");
    }

    @Test
    @DisplayName("Django 마이그레이션 클래스(migrations.Migration)는 모델로 추출하지 않는다")
    void Django_마이그레이션_제외() throws IOException {
        // migrations.CreateModel(fields=[('x', models.CharField())]) 안의 models.*Field 가 모델로 오탐되면 안 됨.
        Path file = writePyFile("""
                from django.db import migrations, models

                class Migration(migrations.Migration):
                    operations = [
                        migrations.CreateModel(name='Article', fields=[
                            ('title', models.CharField(max_length=200)),
                        ]),
                    ]
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Python");

        assertThat(result.dbTables()).isEmpty();
    }

    @Test
    @DisplayName("Django ORM 접근(Entity.objects.method) — 엔티티/읽기쓰기 구분으로 dbAccesses 추출")
    void Django_ORM_접근_추출() throws IOException {
        Path file = writePyFile("""
                from articles.models import Article

                def list_articles():
                    return Article.objects.filter(published=True)

                def make_article():
                    return Article.objects.create(title='x')
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Python");

        assertThat(result.dbAccesses()).extracting(DbAccess::entityClass).contains("Article");
        // filter=읽기, create=쓰기 둘 다 추출
        assertThat(result.dbAccesses()).anyMatch(a -> a.entityClass().equals("Article") && !a.isWrite());
        assertThat(result.dbAccesses()).anyMatch(a -> a.entityClass().equals("Article") && a.isWrite());
    }

    // SQLAlchemy 선언형 모델/접근 감지 테스트
    @Test
    @DisplayName("Flask-SQLAlchemy db.Model 상속 + 믹스인 조합 모델에서 테이블명 추출(믹스인은 제외)")
    void SQLAlchemy_FlaskModel_추출() throws IOException {
        Path file = writePyFile("""
                from conduit.database import Column, Model, SurrogatePK, db

                class SurrogatePK(object):
                    id = db.Column(db.Integer, primary_key=True)

                class User(SurrogatePK, Model):
                    __tablename__ = 'users'
                    username = Column(db.String(80))

                class Article(SurrogatePK, Model):
                    __tablename__ = 'articles'
                    title = db.Column(db.String(100))
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Python");

        // base=Model + 필드신호로 감지, 순수 믹스인 SurrogatePK(object)는 제외
        assertThat(result.dbTables()).extracting(DbTableInfo::tableName)
                .containsExactlyInAnyOrder("users", "articles");
    }

    @Test
    @DisplayName("SQLAlchemy 접근(Entity.query / session.query(Entity)) — dbAccesses에 읽기로 추출, self/cls는 제외")
    void SQLAlchemy_접근_추출() throws IOException {
        Path file = writePyFile("""
                def list_articles(session):
                    return Article.query.filter_by(published=True).all()

                def get_user(session):
                    return session.query(User).filter_by(id=1).first()

                def mixin_helper(self):
                    return self.query.all()
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Python");

        assertThat(result.dbAccesses()).extracting(DbAccess::entityClass)
                .contains("Article", "User");
        // SQLAlchemy 읽기는 전부 isWrite=false
        assertThat(result.dbAccesses()).allMatch(a -> !a.isWrite());
        // self.query(소문자 수신자)는 엔티티가 아니므로 미추출
        assertThat(result.dbAccesses()).extracting(DbAccess::entityClass)
                .doesNotContain("self");
    }

    // TypeORM 모델/접근 감지 테스트
    @Test
    @DisplayName("TypeORM @Entity — className은 실제 클래스명, 테이블명은 @Entity('x') 인자")
    void TypeORM_Entity_클래스명_추출() throws IOException {
        Path file = writeTsFile("""
                import { Entity, Column } from 'typeorm';

                @Entity('article')
                export class ArticleEntity {
                  @Column() title: string;
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "TypeScript");

        assertThat(result.dbTables()).hasSize(1);
        assertThat(result.dbTables().get(0).tableName()).isEqualTo("article");
        // className은 파일명이 아니라 실제 클래스명 — 접근(Repository<ArticleEntity>) 매칭용
        assertThat(result.dbTables().get(0).className()).isEqualTo("ArticleEntity");
    }

    @Test
    @DisplayName("TypeORM 접근 — Repository<Entity> 필드 매핑 후 this.repo.find/save 를 읽기/쓰기로, getRepository(Entity)는 읽기")
    void TypeORM_접근_추출() throws IOException {
        Path file = writeTsFile("""
                import { Repository, getRepository } from 'typeorm';

                export class ArticleService {
                  constructor(
                    private readonly articleRepository: Repository<ArticleEntity>,
                    private readonly userRepository: Repository<UserEntity>,
                  ) {}

                  async find() {
                    return this.articleRepository.findOne(1);
                  }
                  async create(a) {
                    return this.articleRepository.save(a);
                  }
                  async users() {
                    return getRepository(UserEntity).createQueryBuilder('u').getMany();
                  }
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "TypeScript");

        // findOne=읽기, save=쓰기 둘 다 ArticleEntity 로 해소
        assertThat(result.dbAccesses()).anyMatch(a -> a.entityClass().equals("ArticleEntity") && !a.isWrite());
        assertThat(result.dbAccesses()).anyMatch(a -> a.entityClass().equals("ArticleEntity") && a.isWrite());
        // getRepository(UserEntity) = 읽기
        assertThat(result.dbAccesses()).anyMatch(a -> a.entityClass().equals("UserEntity") && !a.isWrite());
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
    @DisplayName("Java @Transactional 어노테이션 메서드를 transactionalMethods에 포함한다")
    void Java_Transactional_어노테이션_감지() throws IOException {
        Path file = writeJavaFile("""
                public class FooRepositoryImpl {
                    @Transactional
                    public void deleteByFooId(String id) {}

                    public void findByFooId(String id) {}
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Java");

        assertThat(result.transactionalMethods()).contains("deleteByFooId");
        assertThat(result.transactionalMethods()).doesNotContain("findByFooId");
    }

    @Test
    @DisplayName("접근제어자 없는 인터페이스 추상 메서드(JpaRepository 파생 쿼리)도 감지한다")
    void Java_Transactional_인터페이스_추상메서드_감지() throws IOException {
        // JpaRepository 파생 쿼리는 인터페이스 추상 메서드라 public/private 같은 제어자가 없는 게 흔함
        Path file = writeJavaFile("""
                public interface FooJpaRepository extends JpaRepository<Foo, java.util.UUID> {
                    @Transactional
                    void deleteByUserIdAndPostId(java.util.UUID userId, java.util.UUID postId);
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Java");

        assertThat(result.transactionalMethods()).contains("deleteByUserIdAndPostId");
    }

    @Test
    @DisplayName("인터페이스 파일의 추상 메서드를 interfaceMethods에 포함한다 (BROKEN_INTERFACE_CHAIN 판정용)")
    void Java_인터페이스_추상메서드_감지() throws IOException {
        Path file = writeJavaFile("""
                public interface FooService {
                    void doWork();
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Java");

        assertThat(result.interfaceMethods()).contains("doWork");
    }

    @Test
    @DisplayName("일반 클래스의 메서드는 interfaceMethods에 포함하지 않는다")
    void Java_클래스_메서드는_interfaceMethods_제외() throws IOException {
        Path file = writeJavaFile("""
                public class FooServiceImpl {
                    public void doWork() {}
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Java");

        assertThat(result.interfaceMethods()).doesNotContain("doWork");
    }

    @Test
    @DisplayName("★도그푸딩 실측: JpaRepository 파생 인터페이스는 interfaceMethods에서 제외한다(프록시 구현 — @Override 체인 없음)")
    void Java_JpaRepository_파생_인터페이스는_interfaceMethods_제외() throws IOException {
        Path file = writeJavaFile("""
                public interface FooJpaRepository extends JpaRepository<Foo, java.util.UUID> {
                    java.util.Optional<Foo> findByName(String name);
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Java");

        assertThat(result.interfaceMethods()).isEmpty();
    }

    @Test
    @DisplayName("★도그푸딩 실측: JpaRepository+도메인 포트를 함께 extends하는 인터페이스는 포트 메서드도 구현으로 인정한다")
    void Java_JpaRepository와_도메인포트_함께_extends() throws IOException {
        Path file = writeJavaFile("""
                public interface FooJpaRepository extends JpaRepository<Foo, java.util.UUID>, FooRepository {
                    java.util.Optional<Foo> findByName(String name);
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Java");

        assertThat(result.implementedInterfaces()).contains("FooRepository");
        assertThat(result.implementedInterfaces()).doesNotContain("JpaRepository");
    }

    @Test
    @DisplayName("★도그푸딩 실측: 인터페이스 안 중첩 record의 메서드는 interfaceMethods에서 제외한다")
    void Java_인터페이스_중첩레코드_메서드는_interfaceMethods_제외() throws IOException {
        Path file = writeJavaFile("""
                public interface FooPort {
                    FooView getFoo(String id);

                    record FooView(String id, String name) {
                        public boolean matches(String other) {
                            return name.equals(other);
                        }
                    }
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Java");

        assertThat(result.interfaceMethods()).contains("getFoo");
        assertThat(result.interfaceMethods()).doesNotContain("matches");
    }

    @Test
    @DisplayName("TypeScript는 interfaceMethods를 추출하지 않는다(Java/Kotlin 전용 개념)")
    void TypeScript는_interfaceMethods_추출_안함() throws IOException {
        Path file = writeTsFile("""
                interface FooService {
                    doWork(): void;
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "TypeScript");

        assertThat(result.interfaceMethods()).isEmpty();
    }

    @Test
    @DisplayName("TypeScript는 @Transactional 개념이 없어 transactionalMethods를 추출하지 않는다")
    void TypeScript는_Transactional_추출_안함() throws IOException {
        Path file = writeTsFile("""
                class FooService {
                    async deleteFoo(id: string) {}
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "TypeScript");

        assertThat(result.transactionalMethods()).isEmpty();
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

    // ── tree-sitter Python 함수·호출 추출 (regex→AST 전환) ──────────────────

    @Test
    @DisplayName("중첩 함수 뒤 바깥 함수 호출을 바깥 함수에 귀속한다 (tree-sitter, 정규식 오귀속 해소)")
    void Python_중첩함수_호출귀속_treesitter() throws IOException {
        // 정규식은 def 위치 경계로 본문을 나눠 inner 뒤의 helper() 호출까지 inner에 잘못 귀속한다.
        // AST는 가장 가까운 enclosing 함수에 정확히 귀속 — outer→helper, inner→deep.
        Path file = writePyFile("""
                def outer():
                    def inner():
                        deep()
                    helper()

                def helper():
                    pass

                def deep():
                    pass
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Python");

        assertThat(result.functions()).contains("outer", "inner", "helper", "deep");
        assertThat(result.functionCalls().get("inner")).contains("deep");
        assertThat(result.functionCalls().get("inner")).doesNotContain("helper");
        assertThat(result.functionCalls().get("outer")).contains("helper");
    }

    @Test
    @DisplayName("async def 와 메서드를 함수로 추출하고 self/메서드 호출을 잡는다 (tree-sitter)")
    void Python_async_메서드_추출_treesitter() throws IOException {
        Path file = writePyFile("""
                class Service:
                    async def fetch(self):
                        await load()

                    def run(self):
                        self.fetch()
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Python");

        assertThat(result.functions()).contains("fetch", "run");
        assertThat(result.functionCalls().get("fetch")).contains("load");
        assertThat(result.functionCalls().get("run")).contains("fetch");
    }

    @Test
    @DisplayName("주석·docstring·문자열 속 식별자를 호출로 오인하지 않는다 (tree-sitter, B-10 근본 해소)")
    void Python_주석_문자열_식별자_호출_제외() throws IOException {
        Path file = writePyFile("""
                def run():
                    # fake_call() 처럼 보이는 주석
                    \"\"\"docstring with also_fake() inside\"\"\"
                    sql = "select real_looking() from x"
                    real_call()

                def real_call():
                    pass
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Python");

        assertThat(result.functionCalls().get("run")).contains("real_call");
        assertThat(result.functionCalls().get("run"))
                .doesNotContain("fake_call", "also_fake", "real_looking");
    }

    @Test
    @DisplayName("Class.method() 한정 호출을 Class::method 형식으로 기록한다 (tree-sitter)")
    void Python_한정호출_추출_treesitter() throws IOException {
        Path file = writePyFile("""
                def run():
                    Logger.warning("x")
                    helper()

                def helper():
                    pass
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Python");

        assertThat(result.functionCalls().get("run")).contains("Logger::warning", "helper");
        // 대문자 수신자의 메서드명을 bare 로도 기록하면 동명 지역 함수에 가짜 엣지가 생기므로 기록하지 않는다.
        assertThat(result.functionCalls().get("run")).doesNotContain("warning");
    }

    // ── tree-sitter TypeScript 함수·호출 추출 (regex→AST 전환) ──────────────

    @Test
    @DisplayName("클래스 메서드를 함수로 추출한다 (tree-sitter, 정규식이 못 잡던 것)")
    void TS_클래스_메서드_추출_treesitter() throws IOException {
        // 정규식 TS 패턴은 `function` 키워드를 요구해 클래스 메서드(`name(){}`)를 전혀 못 잡는다. AST는 method_definition으로 인식.
        Path file = writeTsFile("""
                class UserService {
                    constructor() {}
                    findById(id: number) { return this.load(id); }
                    private load(id: number) { return null; }
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "TypeScript");

        assertThat(result.functions()).contains("findById", "load");
        assertThat(result.functionCalls().get("findById")).contains("load");
    }

    @Test
    @DisplayName("화살표 함수와 중첩 함수 호출 귀속을 정확히 잡는다 (tree-sitter)")
    void TS_화살표_중첩함수_호출귀속_treesitter() throws IOException {
        Path file = writeTsFile("""
                export const outer = () => {
                    const inner = () => { deep(); };
                    helper();
                };
                function helper() {}
                function deep() {}
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "TypeScript");

        assertThat(result.functions()).contains("outer", "inner", "helper", "deep");
        assertThat(result.functionCalls().get("inner")).contains("deep");
        assertThat(result.functionCalls().get("inner")).doesNotContain("helper");
        assertThat(result.functionCalls().get("outer")).contains("helper");
    }

    @Test
    @DisplayName("주석·문자열 속 식별자를 호출로 오인하지 않는다 (tree-sitter, B-10 근본 해소)")
    void TS_주석_문자열_식별자_호출_제외() throws IOException {
        Path file = writeTsFile("""
                function run() {
                    // fakeCall() 처럼 보이는 주석
                    const s = "realLooking() in string";
                    realCall();
                }
                function realCall() {}
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "TypeScript");

        assertThat(result.functionCalls().get("run")).contains("realCall");
        assertThat(result.functionCalls().get("run")).doesNotContain("fakeCall", "realLooking");
    }

    @Test
    @DisplayName("Class.method() 한정 호출을 Class::method 형식으로 기록한다 (tree-sitter)")
    void TS_한정호출_추출_treesitter() throws IOException {
        Path file = writeTsFile("""
                function run() {
                    Logger.warn("x");
                    helper();
                }
                function helper() {}
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "TypeScript");

        assertThat(result.functionCalls().get("run")).contains("Logger::warn", "helper");
        // 대문자 수신자의 메서드명을 bare 로도 기록하면 동명 지역 함수에 가짜 엣지가 생기므로 기록하지 않는다.
        assertThat(result.functionCalls().get("run")).doesNotContain("warn");
    }

    @Test
    @DisplayName(".tsx(JSX)를 tsx 그래머로 파싱해 클래스 메서드를 추출한다 (tree-sitter)")
    void TSX_JSX_클래스_메서드_추출_treesitter() throws IOException {
        // tsx 그래머가 아니면 JSX(<div>)에서 파싱 오류 → 정규식 폴백. 정규식은 클래스 메서드를 못 잡으므로
        // render/label 추출 = tsx 그래머로 파싱 성공 + 메서드 인식 둘 다 증명한다.
        Path file = writeTsxFile("""
                class Widget {
                    render() { return <div>{this.label()}</div>; }
                    label() { return "x"; }
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "TypeScript");

        assertThat(result.functions()).contains("render", "label");
        assertThat(result.functionCalls().get("render")).contains("label");
    }

    // ── tree-sitter JavaScript 함수·호출 추출 (TypeScript 분석기 재사용) ─────

    @Test
    @DisplayName("JavaScript 클래스 메서드를 함수로 추출한다 (tree-sitter, TS 분석기 재사용)")
    void JS_클래스_메서드_추출_treesitter() throws IOException {
        // .js는 typescript 그래머로 파싱. 정규식이 못 잡는 클래스 메서드를 AST가 인식.
        Path file = writeJsFile("""
                class UserService {
                    findById(id) { return this.load(id); }
                    load(id) { return null; }
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "JavaScript");

        assertThat(result.functions()).contains("findById", "load");
        assertThat(result.functionCalls().get("findById")).contains("load");
    }

    @Test
    @DisplayName("CommonJS 멤버 대입 함수(exports.x=function, proto.y=function)를 추출한다 (tree-sitter)")
    void JS_멤버대입_함수_추출_treesitter() throws IOException {
        // 정규식·기존 AST 모두 놓치던 패턴. assignment_expression 좌변 속성명으로 함수 인식.
        Path file = writeJsFile("""
                exports.handle = function(req, res) { return parse(req); };
                Router.prototype.use = function(fn) { register(fn); };
                function parse(r) { return r; }
                function register(f) {}
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "JavaScript");

        assertThat(result.functions()).contains("handle", "use", "parse", "register");
        assertThat(result.functionCalls().get("handle")).contains("parse");
        assertThat(result.functionCalls().get("use")).contains("register");
    }

    @Test
    @DisplayName(".jsx(JSX)를 tsx 그래머로 파싱해 함수를 추출한다 (tree-sitter)")
    void JSX_함수_추출_treesitter() throws IOException {
        // .jsx는 tsx 그래머로 파싱해야 JSX(<button>)에서 오류가 없다.
        Path file = writeJsxFile("""
                function App() {
                    const onClick = () => { handle(); };
                    return <button onClick={onClick}>Hi</button>;
                }
                function handle() {}
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "JavaScript");

        assertThat(result.functions()).contains("App", "onClick", "handle");
        assertThat(result.functionCalls().get("onClick")).contains("handle");
    }

    // ── tree-sitter Go 함수·호출 추출 (regex→AST 전환) ──────────────────────

    @Test
    @DisplayName("일반 함수와 리시버 메서드를 추출하고 호출을 귀속한다 (tree-sitter)")
    void Go_함수_리시버메서드_추출_treesitter() throws IOException {
        Path file = writeGoFile("""
                package main

                func run() {
                    helper()
                }

                func (s *Server) Handle() {
                    s.process()
                }

                func helper() {}
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Go");

        assertThat(result.functions()).contains("run", "Handle", "helper");
        assertThat(result.functionCalls().get("run")).contains("helper");
        // 리시버 s *Server의 메서드 호출 s.process()는 선언 타입으로 한정 → Server::process (타입 인지 해소)
        assertThat(result.functionCalls().get("Handle")).contains("Server::process");
        assertThat(result.functionCalls().get("Handle")).doesNotContain("process");
    }

    @Test
    @DisplayName("Type.Method() 한정 호출을 Type::Method 형식으로 기록한다 (tree-sitter)")
    void Go_한정호출_추출_treesitter() throws IOException {
        Path file = writeGoFile("""
                package main

                func run() {
                    Logger.Warn("x")
                    compute()
                }

                func compute() {}
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Go");

        assertThat(result.functionCalls().get("run")).contains("Logger::Warn", "compute");
        // 대문자 피연산자의 메서드명을 bare 로도 기록하면 동명 지역 함수에 가짜 엣지가 생기므로 기록하지 않는다.
        assertThat(result.functionCalls().get("run")).doesNotContain("Warn");
    }

    @Test
    @DisplayName("주석·문자열·import 경로 속 식별자를 호출로 오인하지 않는다 (tree-sitter)")
    void Go_주석_문자열_식별자_호출_제외() throws IOException {
        Path file = writeGoFile("""
                package main

                import "fmt"

                func run() {
                    // fakeCall() 처럼 보이는 주석
                    s := "realLooking() in string"
                    _ = s
                    realCall()
                }

                func realCall() {}
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Go");

        assertThat(result.functionCalls().get("run")).contains("realCall");
        assertThat(result.functionCalls().get("run")).doesNotContain("fakeCall", "realLooking");
    }

    // ── tree-sitter Rust 함수·호출 추출 (regex→AST 전환) ────────────────────

    @Test
    @DisplayName("일반 함수와 impl 메서드를 추출하고 호출을 귀속한다 (tree-sitter)")
    void Rust_함수_impl메서드_추출_treesitter() throws IOException {
        Path file = writeRustFile("""
                fn run() {
                    helper();
                }

                impl Server {
                    fn handle(&self) {
                        self.process();
                    }
                }

                fn helper() {}
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Rust");

        assertThat(result.functions()).contains("run", "handle", "helper");
        assertThat(result.functionCalls().get("run")).contains("helper");
        // self.process()는 impl 대상 타입으로 한정 → Server::process (타입 인지 해소)
        assertThat(result.functionCalls().get("handle")).contains("Server::process");
        assertThat(result.functionCalls().get("handle")).doesNotContain("process");
    }

    @Test
    @DisplayName("Rust 파라미터 타입 수신자를 선언 타입으로 한정한다(repo: &UserRepo → repo.save())")
    void Rust_파라미터_수신자_타입_해소() throws IOException {
        Path file = writeRustFile("""
                fn dispatch(repo: &UserRepo) {
                    repo.save();
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Rust");

        assertThat(result.functionCalls().get("dispatch")).contains("UserRepo::save");
        assertThat(result.functionCalls().get("dispatch")).doesNotContain("save");
    }

    @Test
    @DisplayName("Rust struct/trait 선언명을 declaredTypes로 방출한다")
    void Rust_declaredTypes_추출() throws IOException {
        Path file = writeRustFile("""
                pub(crate) struct Core {}
                trait Sink {}

                impl Core {
                    fn run(&self) { self.step(); }
                    fn step(&self) {}
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Rust");

        assertThat(result.declaredTypes()).contains("Core", "Sink");
        assertThat(result.functionCalls().get("run")).contains("Core::step");
    }

    @Test
    @DisplayName("Rust #[test]·#[cfg(test)] mod 함수를 testMethods로 표시한다(HIGH_FAN_OUT 제외용)")
    void Rust_테스트함수_표시() throws IOException {
        Path file = writeRustFile("""
                fn prod() {}

                #[test]
                fn direct_test() {}

                #[cfg(test)]
                mod tests {
                    #[test]
                    fn inside_mod_test() {}

                    fn helper_in_test_mod() {}
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Rust");

        assertThat(result.testMethods()).contains("direct_test", "inside_mod_test", "helper_in_test_mod");
        assertThat(result.testMethods()).doesNotContain("prod");
    }

    @Test
    @DisplayName("Type::method() 한정 호출은 Type::method, 모듈 경로는 bare로 기록한다 (tree-sitter)")
    void Rust_한정호출_추출_treesitter() throws IOException {
        Path file = writeRustFile("""
                fn run() {
                    let v = Vec::new();
                    mem::swap(a, b);
                    compute();
                }

                fn compute() {}
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Rust");

        // 대문자 path(Type) → 한정, 소문자 path(module) → bare
        assertThat(result.functionCalls().get("run")).contains("Vec::new", "swap", "compute");
        // 대문자 path의 메서드명을 bare 로도 기록하면 동명 지역 함수에 가짜 엣지가 생기므로 기록하지 않는다.
        assertThat(result.functionCalls().get("run")).doesNotContain("new");
    }

    @Test
    @DisplayName("trait 시그니처는 함수로 추출하고, 주석·문자열·매크로 속 식별자는 호출로 오인하지 않는다 (tree-sitter)")
    void Rust_trait시그니처_주석_매크로_제외_treesitter() throws IOException {
        Path file = writeRustFile("""
                trait Handler {
                    fn on_event(&self);
                }

                fn run() {
                    // fake_call() 처럼 보이는 주석
                    let s = "real_looking() in string";
                    println!("also_fake()");
                    real_call();
                }

                fn real_call() {}
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Rust");

        assertThat(result.functions()).contains("on_event", "run", "real_call");
        assertThat(result.functionCalls().get("run")).contains("real_call");
        assertThat(result.functionCalls().get("run"))
                .doesNotContain("fake_call", "real_looking", "also_fake", "println");
    }

    // ── Swift (tree-sitter) ──────────────────────────────────────────────────

    @Test
    @DisplayName("Swift 함수·생성자(init)·프로토콜 메서드를 추출하고 호출을 가장 가까운 정의에 귀속한다 (tree-sitter)")
    void Swift_함수_init_프로토콜메서드_추출_treesitter() throws IOException {
        Path file = writeSwiftFile("""
                protocol Greeter {
                    func greet() -> String
                }

                class Service {
                    let repo: UserRepo
                    init(repo: UserRepo) {
                        self.repo = repo
                    }
                    func run(user: User) {
                        repo.save(user)
                        print("done")
                    }
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Swift");

        // 프로토콜 메서드·생성자(정규식이 못 잡던 init)·일반 메서드 모두 추출
        assertThat(result.functions()).contains("greet", "init", "run");
        // navigation 호출(repo.save)은 메서드명 bare, bare 호출(print)도 호출로 귀속
        assertThat(result.functionCalls().get("run")).contains("save", "print");
    }

    @Test
    @DisplayName("Swift 대문자 수신자(Type/enum)는 Type::method로 한정, 소문자 변수·self는 bare (tree-sitter)")
    void Swift_대문자수신자_한정_treesitter() throws IOException {
        Path file = writeSwiftFile("""
                class Worker {
                    func run(handler: Handler) {
                        Logger.log("start")
                        handler.handle()
                        self.cleanup()
                    }
                    func cleanup() {}
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Swift");

        // 대문자 단순 식별자 수신자 → Type::method 한정 (Logger.log)
        assertThat(result.functionCalls().get("run")).contains("Logger::log");
        // 소문자 변수·self 수신자 → bare 메서드명 (로컬 메서드와 매칭되게)
        assertThat(result.functionCalls().get("run")).contains("handle", "cleanup");
        assertThat(result.functionCalls().get("run")).doesNotContain("Logger", "log");
    }

    @Test
    @DisplayName("Swift 주석·문자열 보간(\\(expr)) 속 식별자는 호출로 오인하지 않는다 (tree-sitter)")
    void Swift_주석_문자열보간_제외_treesitter() throws IOException {
        Path file = writeSwiftFile("""
                func run() {
                    // fakeCall() 처럼 보이는 주석
                    let s = "alsoFake() in string \\(realInterp())"
                    realCall()
                }
                func realCall() {}
                func realInterp() -> String { return "" }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Swift");

        assertThat(result.functionCalls().get("run")).contains("realCall");
        // 문자열 보간 \\(realInterp()) 안의 호출은 AST가 식으로 인식 — 보간은 실제 실행되므로 호출로 귀속됨
        assertThat(result.functionCalls().get("run")).doesNotContain("fakeCall", "alsoFake");
    }

    // ── 함수 정의 줄 번호·컬럼 추출 (VS Code 인라인 경고용, 11개 언어 전체) ──────────

    @Test
    @DisplayName("Java 메서드의 정의 시작 줄을 1-indexed로 추출한다")
    void Java_함수_줄번호_추출() throws IOException {
        Path file = writeJavaFile("""
                package com.example;
                public class UserService {
                    public User findById(Long id) {
                        return null;
                    }

                    public void save(User user) {
                    }
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Java");

        assertThat(result.functionLines()).containsEntry("findById", 3);
        assertThat(result.functionLines()).containsEntry("save", 7);
    }

    @Test
    @DisplayName("Java 동명 오버로드는 첫 정의의 줄만 유지한다")
    void Java_동명_오버로드_첫정의_줄_유지() throws IOException {
        Path file = writeJavaFile("""
                package com.example;
                public class Calculator {
                    public int add(int a, int b) {
                        return a + b;
                    }

                    public double add(double a, double b) {
                        return a + b;
                    }
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Java");

        assertThat(result.functionLines()).containsEntry("add", 3);
    }

    @Test
    @DisplayName("TypeScript 함수 선언과 화살표 함수의 정의 시작 줄을 추출한다")
    void TypeScript_함수_줄번호_추출() throws IOException {
        Path file = writeTsFile("""
                function greet(name: string): string {
                    return `hi ${name}`;
                }

                const add = (a: number, b: number) => {
                    return a + b;
                };
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "TypeScript");

        assertThat(result.functionLines()).containsEntry("greet", 1);
        assertThat(result.functionLines()).containsEntry("add", 5);
    }

    @Test
    @DisplayName("Java 메서드 식별자의 시작 컬럼을 0-indexed로 추출한다")
    void Java_함수_컬럼_추출() throws IOException {
        Path file = writeJavaFile("""
                package com.example;
                public class UserService {
                    public User findById(Long id) {
                        return null;
                    }
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Java");

        // "    public User findById(Long id) {" — findById는 16번째 문자(0-indexed)에서 시작
        assertThat(result.functionColumns()).containsEntry("findById", 16);
    }

    @Test
    @DisplayName("TypeScript 함수 선언과 화살표 함수 식별자의 시작 컬럼을 0-indexed로 추출한다")
    void TypeScript_함수_컬럼_추출() throws IOException {
        Path file = writeTsFile("""
                function greet(name: string): string {
                    return `hi ${name}`;
                }

                const add = (a: number, b: number) => {
                    return a + b;
                };
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "TypeScript");

        // "function greet(..." — greet는 9번째 문자(0-indexed)에서 시작
        assertThat(result.functionColumns()).containsEntry("greet", 9);
        // "const add = (..." — add는 6번째 문자(0-indexed)에서 시작
        assertThat(result.functionColumns()).containsEntry("add", 6);
    }

    @Test
    @DisplayName("Python 메서드의 정의 시작 줄·식별자 컬럼을 추출한다")
    void Python_함수_줄번호_컬럼_추출() throws IOException {
        Path file = writePyFile("""
                class UserService:
                    def find_by_id(self, id):
                        return None
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Python");

        assertThat(result.functionLines()).containsEntry("find_by_id", 2);
        // "    def find_by_id(..." — find_by_id는 8번째 문자(0-indexed)에서 시작
        assertThat(result.functionColumns()).containsEntry("find_by_id", 8);
    }

    @Test
    @DisplayName("Go 함수의 정의 시작 줄·식별자 컬럼을 추출한다")
    void Go_함수_줄번호_컬럼_추출() throws IOException {
        Path file = writeGoFile("""
                package main

                func FindById(id int) int {
                	return id
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Go");

        assertThat(result.functionLines()).containsEntry("FindById", 3);
        // "func FindById(..." — FindById는 5번째 문자(0-indexed)에서 시작
        assertThat(result.functionColumns()).containsEntry("FindById", 5);
    }

    @Test
    @DisplayName("Rust 메서드의 정의 시작 줄·식별자 컬럼을 추출한다")
    void Rust_함수_줄번호_컬럼_추출() throws IOException {
        Path file = writeRustFile("""
                struct UserService;

                impl UserService {
                    fn find_by_id(&self, id: i32) -> i32 {
                        id
                    }
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Rust");

        assertThat(result.functionLines()).containsEntry("find_by_id", 4);
        // "    fn find_by_id(..." — find_by_id는 7번째 문자(0-indexed)에서 시작
        assertThat(result.functionColumns()).containsEntry("find_by_id", 7);
    }

    @Test
    @DisplayName("C# 메서드의 정의 시작 줄·식별자 컬럼을 추출한다")
    void CSharp_함수_줄번호_컬럼_추출() throws IOException {
        Path file = tempDir.resolve("UserService.cs");
        Files.writeString(file, """
                public class UserService {
                    public User FindById(int id) {
                        return null;
                    }
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "C#");

        assertThat(result.functionLines()).containsEntry("FindById", 2);
        // "    public User FindById(..." — FindById는 16번째 문자(0-indexed)에서 시작
        assertThat(result.functionColumns()).containsEntry("FindById", 16);
    }

    @Test
    @DisplayName("Ruby 메서드의 정의 시작 줄·식별자 컬럼을 추출한다")
    void Ruby_함수_줄번호_컬럼_추출() throws IOException {
        Path file = tempDir.resolve("user_service.rb");
        Files.writeString(file, """
                class UserService
                  def find_by_id(id)
                    nil
                  end
                end
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Ruby");

        assertThat(result.functionLines()).containsEntry("find_by_id", 2);
        // "  def find_by_id(..." — find_by_id는 6번째 문자(0-indexed)에서 시작
        assertThat(result.functionColumns()).containsEntry("find_by_id", 6);
    }

    @Test
    @DisplayName("PHP 메서드의 정의 시작 줄·식별자 컬럼을 추출한다")
    void PHP_함수_줄번호_컬럼_추출() throws IOException {
        Path file = tempDir.resolve("UserService.php");
        Files.writeString(file, """
                <?php
                class UserService {
                    public function findById($id) {
                        return null;
                    }
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "PHP");

        assertThat(result.functionLines()).containsEntry("findById", 3);
        // "    public function findById(..." — findById는 20번째 문자(0-indexed)에서 시작
        assertThat(result.functionColumns()).containsEntry("findById", 20);
    }

    @Test
    @DisplayName("C 함수의 정의 시작 줄·식별자 컬럼을 추출한다")
    void C_함수_줄번호_컬럼_추출() throws IOException {
        Path file = tempDir.resolve("user_service.c");
        Files.writeString(file, """
                int find_by_id(int id) {
                    return id;
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "C");

        assertThat(result.functionLines()).containsEntry("find_by_id", 1);
        // "int find_by_id(..." — find_by_id는 4번째 문자(0-indexed)에서 시작
        assertThat(result.functionColumns()).containsEntry("find_by_id", 4);
    }

    @Test
    @DisplayName("C++ 메서드의 정의 시작 줄·식별자 컬럼을 추출한다")
    void Cpp_함수_줄번호_컬럼_추출() throws IOException {
        Path file = tempDir.resolve("UserService.cpp");
        Files.writeString(file, """
                class UserService {
                public:
                    int findById(int id) {
                        return id;
                    }
                };
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "C++");

        assertThat(result.functionLines()).containsEntry("findById", 3);
        // "    int findById(..." — findById는 8번째 문자(0-indexed)에서 시작
        assertThat(result.functionColumns()).containsEntry("findById", 8);
    }

    @Test
    @DisplayName("Swift 메서드·생성자의 정의 시작 줄·식별자 컬럼을 추출한다")
    void Swift_함수_줄번호_컬럼_추출() throws IOException {
        Path file = writeSwiftFile("""
                class UserService {
                    func findById(id: Int) -> Int {
                        return id
                    }

                    init(id: Int) {
                    }
                }
                """);

        ParsedFile result = analyzer.analyze(file, tempDir, "Swift");

        assertThat(result.functionLines()).containsEntry("findById", 2);
        // "    func findById(..." — findById는 9번째 문자(0-indexed)에서 시작
        assertThat(result.functionColumns()).containsEntry("findById", 9);
        assertThat(result.functionLines()).containsEntry("init", 6);
        // "    init(..." — init은 4번째 문자(0-indexed)에서 시작
        assertThat(result.functionColumns()).containsEntry("init", 4);
    }

    // ── 헬퍼 ────────────────────────────────────────────────────────────────

    private Path writeSwiftFile(String content) throws IOException {
        Path file = tempDir.resolve("TestFile.swift");
        Files.writeString(file, content);
        return file;
    }

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

    private Path writeTsxFile(String content) throws IOException {
        Path file = tempDir.resolve("testFile.tsx");
        Files.writeString(file, content);
        return file;
    }

    private Path writeJsFile(String content) throws IOException {
        Path file = tempDir.resolve("testFile.js");
        Files.writeString(file, content);
        return file;
    }

    private Path writeJsxFile(String content) throws IOException {
        Path file = tempDir.resolve("testFile.jsx");
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

    private Path writeRustFile(String content) throws IOException {
        Path file = tempDir.resolve("test_file.rs");
        Files.writeString(file, content);
        return file;
    }
}
