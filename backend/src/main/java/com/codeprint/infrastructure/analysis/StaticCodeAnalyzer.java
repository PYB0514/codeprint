// 언어별 정규식으로 함수 정의, import, 주석을 추출하는 정적 분석기
package com.codeprint.infrastructure.analysis;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class StaticCodeAnalyzer {

    // tree-sitter 기반 Java 분석기 — 정규식보다 정확. native 로드 실패 시 자동으로 정규식 폴백된다.
    private final TreeSitterJavaAnalyzer treeSitterJava = new TreeSitterJavaAnalyzer();
    // tree-sitter 기반 Python 분석기 — 중첩 함수·메서드 호출 귀속 정확. native 로드 실패 시 정규식 폴백.
    private final TreeSitterPythonAnalyzer treeSitterPython = new TreeSitterPythonAnalyzer();
    // tree-sitter 기반 TypeScript/JavaScript 분석기 — 정규식이 못 잡는 클래스 메서드 회복. native 로드 실패 시 정규식 폴백.
    private final TreeSitterTypescriptAnalyzer treeSitterTypescript = new TreeSitterTypescriptAnalyzer();
    // tree-sitter 기반 Go 분석기 — 리시버 메서드·정확한 호출 귀속. native 로드 실패 시 정규식 폴백.
    private final TreeSitterGoAnalyzer treeSitterGo = new TreeSitterGoAnalyzer();
    // tree-sitter 기반 Rust 분석기 — impl 메서드·trait 시그니처·정확한 호출 귀속. native 로드 실패 시 정규식 폴백.
    private final TreeSitterRustAnalyzer treeSitterRust = new TreeSitterRustAnalyzer();
    // tree-sitter 기반 C# 분석기 — 로컬 함수·인터페이스 메서드·정확한 호출 귀속. native 로드 실패 시 정규식 폴백.
    private final TreeSitterCSharpAnalyzer treeSitterCSharp = new TreeSitterCSharpAnalyzer();
    // tree-sitter 기반 Ruby 분석기 — 싱글톤 메서드·블록 내 호출·정확한 호출 귀속. native 로드 실패 시 정규식 폴백.
    private final TreeSitterRubyAnalyzer treeSitterRuby = new TreeSitterRubyAnalyzer();
    // tree-sitter 기반 PHP 분석기 — ->/:: 호출·최상위 함수·정확한 호출 귀속. native 로드 실패 시 정규식 폴백.
    private final TreeSitterPhpAnalyzer treeSitterPhp = new TreeSitterPhpAnalyzer();
    // tree-sitter 기반 C 분석기 — 선언자 체인에서 함수명 추출·호출 귀속. C는 정규식 미지원이라 AST가 유일 경로.
    private final TreeSitterCAnalyzer treeSitterC = new TreeSitterCAnalyzer();
    // tree-sitter 기반 C++ 분석기 — 클래스 메서드·qualified 정의·연산자·소멸자·템플릿. C++도 정규식 미지원이라 AST가 유일 경로.
    private final TreeSitterCppAnalyzer treeSitterCpp = new TreeSitterCppAnalyzer();
    // tree-sitter 기반 Swift 분석기 — 생성자(init)·프로토콜 메서드·navigation 호출 정확 귀속. native 로드 실패 시 정규식 폴백.
    private final TreeSitterSwiftAnalyzer treeSitterSwift = new TreeSitterSwiftAnalyzer();

    // 단일 소스 파일을 분석하여 함수명, import, 주석 등을 추출
    public ParsedFile analyze(Path file, Path repoRoot, String language) throws IOException {
        String content = Files.readString(file, StandardCharsets.UTF_8);
        // UTF-8 BOM 제거 — tree-sitter 바이트 오프셋은 BOM을 제외하는데 content.getBytes()는 BOM(3바이트)을 포함해
        // 오프셋이 어긋나면 모든 식별자 추출이 밀린다(.NET 소스는 BOM 저장이 흔함). 정규식 경로에도 무해(보이지 않는 선두 문자 제거).
        if (!content.isEmpty() && content.charAt(0) == '\uFEFF') content = content.substring(1);
        String relativePath = repoRoot.relativize(file).toString().replace("\\", "/");

        // docker-compose.yml은 프로그래밍 언어 소스가 아니라 environment 블록의 서비스 호스트 매핑만
        // 필요 — tree-sitter/함수 추출 파이프라인 전체를 우회하고 최소 필드만 채운 ParsedFile을 즉시 반환한다
        // (SERVICE_CALL_CHAIN "변수 조합 URL" ②, decisions/DECISIONS_ANALYSIS.md 참조).
        if (language.equals("DockerCompose")) {
            Map<String, String> composeEnvHosts = extractComposeEnvHosts(content);
            return new ParsedFile(relativePath, language, List.of(), List.of(), null, Map.of(),
                    Map.of(), List.of(), List.of(), null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Map.of(),
                    List.of(), List.of(), List.of(), null, Map.of(), List.of(), Map.of(), Map.of(), List.of(), List.of(), null,
                    List.of(), null, List.of(), composeEnvHosts);
        }

        // 식별자 검출기용 — 주석 본문을 공백으로 치환한 길이 보존 사본 (B-10 Stage 1).
        // 주석/문자열 페이로드를 읽는 검출기(주석 라벨·API 경로·raw SQL 등)는 원본 content를 그대로 쓴다.
        String masked = maskComments(content, language);

        // Java·Python·TypeScript/JavaScript·Go·Rust·C#·Ruby·PHP·C 함수·호출은 tree-sitter(AST)로 추출 — 오탐 제거·정확한 호출 귀속(중첩/메서드).
        // tree-sitter는 raw content를 직접 파싱(AST가 주석·문자열을 구분하므로 masking 불필요). 실패 시 정규식 폴백.
        List<String> functions;
        Map<String, List<String>> functionCalls;
        // 파일이 선언한 클래스/인터페이스명 — 파일명≠클래스명 언어(TS 등)의 Type::method 해소용. 기본 빈 목록.
        List<String> declaredTypes = List.of();
        // 테스트 함수명 — 파일명으로 못 거르는 인라인 테스트(Rust #[test]) HIGH_FAN_OUT 제외용. 기본 빈 목록.
        List<String> testMethods = List.of();
        Optional<TreeSitterJavaAnalyzer.Result> javaTs =
                language.equals("Java") ? treeSitterJava.parse(content) : Optional.empty();
        Optional<TreeSitterPythonAnalyzer.Result> pyTs =
                language.equals("Python") ? treeSitterPython.parse(content) : Optional.empty();
        // JavaScript도 동일 분석기로 처리 — typescript 그래머가 JS를 파싱하고, JSX(.jsx/.tsx)는 tsx 그래머 필요(확장자 판정).
        boolean isTsOrJs = language.equals("TypeScript") || language.equals("JavaScript");
        boolean useJsxGrammar = relativePath.endsWith(".tsx") || relativePath.endsWith(".jsx");
        Optional<TreeSitterTypescriptAnalyzer.Result> tsTs =
                isTsOrJs ? treeSitterTypescript.parse(content, useJsxGrammar) : Optional.empty();
        Optional<TreeSitterGoAnalyzer.Result> goTs =
                language.equals("Go") ? treeSitterGo.parse(content) : Optional.empty();
        Optional<TreeSitterRustAnalyzer.Result> rustTs =
                language.equals("Rust") ? treeSitterRust.parse(content) : Optional.empty();
        Optional<TreeSitterCSharpAnalyzer.Result> csTs =
                language.equals("C#") ? treeSitterCSharp.parse(content) : Optional.empty();
        Optional<TreeSitterRubyAnalyzer.Result> rubyTs =
                language.equals("Ruby") ? treeSitterRuby.parse(content) : Optional.empty();
        Optional<TreeSitterPhpAnalyzer.Result> phpTs =
                language.equals("PHP") ? treeSitterPhp.parse(content) : Optional.empty();
        Optional<TreeSitterCAnalyzer.Result> cTs =
                language.equals("C") ? treeSitterC.parse(content) : Optional.empty();
        Optional<TreeSitterCppAnalyzer.Result> cppTs =
                language.equals("C++") ? treeSitterCpp.parse(content) : Optional.empty();
        Optional<TreeSitterSwiftAnalyzer.Result> swiftTs =
                language.equals("Swift") ? treeSitterSwift.parse(content) : Optional.empty();
        // 함수명 → 정의 시작 줄(1-indexed). 11개 언어 모두 채워짐(정규식 폴백 경로만 빈 맵) — VS Code 인라인 경고용.
        Map<String, Integer> functionLines = Map.of();
        // 함수명 → 식별자 시작 컬럼(0-indexed). 11개 언어 모두 채워짐(정규식 폴백 경로만 빈 맵) — 인라인 경고 밑줄 정밀도용.
        Map<String, Integer> functionColumns = Map.of();
        if (javaTs.isPresent()) {
            functions = javaTs.get().functions();
            functionCalls = javaTs.get().functionCalls();
            functionLines = javaTs.get().functionLines();
            functionColumns = javaTs.get().functionColumns();
        } else if (pyTs.isPresent()) {
            functions = pyTs.get().functions();
            functionCalls = pyTs.get().functionCalls();
            declaredTypes = pyTs.get().declaredTypes();
            functionLines = pyTs.get().functionLines();
            functionColumns = pyTs.get().functionColumns();
        } else if (tsTs.isPresent()) {
            functions = tsTs.get().functions();
            functionCalls = tsTs.get().functionCalls();
            declaredTypes = tsTs.get().declaredTypes();
            functionLines = tsTs.get().functionLines();
            functionColumns = tsTs.get().functionColumns();
        } else if (goTs.isPresent()) {
            functions = goTs.get().functions();
            functionCalls = goTs.get().functionCalls();
            declaredTypes = goTs.get().declaredTypes();
            functionLines = goTs.get().functionLines();
            functionColumns = goTs.get().functionColumns();
        } else if (rustTs.isPresent()) {
            functions = rustTs.get().functions();
            functionCalls = rustTs.get().functionCalls();
            declaredTypes = rustTs.get().declaredTypes();
            testMethods = rustTs.get().testFunctions();
            functionLines = rustTs.get().functionLines();
            functionColumns = rustTs.get().functionColumns();
        } else if (csTs.isPresent()) {
            functions = csTs.get().functions();
            functionCalls = csTs.get().functionCalls();
            functionLines = csTs.get().functionLines();
            functionColumns = csTs.get().functionColumns();
        } else if (rubyTs.isPresent()) {
            functions = rubyTs.get().functions();
            functionCalls = rubyTs.get().functionCalls();
            functionLines = rubyTs.get().functionLines();
            functionColumns = rubyTs.get().functionColumns();
        } else if (phpTs.isPresent()) {
            functions = phpTs.get().functions();
            functionCalls = phpTs.get().functionCalls();
            functionLines = phpTs.get().functionLines();
            functionColumns = phpTs.get().functionColumns();
        } else if (cTs.isPresent()) {
            functions = cTs.get().functions();
            functionCalls = cTs.get().functionCalls();
            functionLines = cTs.get().functionLines();
            functionColumns = cTs.get().functionColumns();
        } else if (cppTs.isPresent()) {
            functions = cppTs.get().functions();
            functionCalls = cppTs.get().functionCalls();
            functionLines = cppTs.get().functionLines();
            functionColumns = cppTs.get().functionColumns();
        } else if (swiftTs.isPresent()) {
            functions = swiftTs.get().functions();
            functionCalls = swiftTs.get().functionCalls();
            functionLines = swiftTs.get().functionLines();
            functionColumns = swiftTs.get().functionColumns();
        } else {
            functions = extractFunctions(masked, language);
            functionCalls = extractFunctionCalls(masked, language, functions);
        }

        // 파일 내 동명 정의 횟수 집계 후 functions 디둡 (AST·regex 공통). ≥2면 동명 머지 노드 →
        // GraphBuilder가 노드 메타에 표시하고 HIGH_FAN_OUT 정밀 가드가 union-부풀린 fan-out을 제외한다.
        Map<String, Integer> functionDefCounts = new LinkedHashMap<>();
        for (String f : functions) functionDefCounts.merge(f, 1, Integer::sum);
        functions = new ArrayList<>(new LinkedHashSet<>(functions));

        List<String> imports = extractImports(masked, language);
        String fileComment = extractFileComment(content, language);
        Map<String, String> functionComments = extractFunctionComments(content, language);
        List<String> valueReferencedFunctions = extractValueReferencedFunctions(masked, functions);
        List<String> instantiatedClasses = extractInstantiatedClasses(masked);
        List<DbTableInfo> dbTables = extractDbTables(content, language, relativePath);
        String repositoryEntityClass = extractRepositoryEntityClass(masked, language);
        List<ColumnInfo> entityColumns = extractEntityColumns(masked, language);
        List<String> apiCalls = extractApiCalls(content, language);
        List<String> controllerMappings = extractControllerMappings(content, language);
        Map<String, String> controllerMappingFunctions = extractControllerMappingFunctions(content, language);
        List<String> implementedInterfaces = extractImplementedInterfaces(masked, language);
        List<String> asyncMethods = extractAsyncMethods(masked, language);
        List<String> jsxComponents = extractJsxComponents(masked, relativePath);
        List<RawSqlAccess> rawSqlAccesses = extractRawSqlAccesses(content);
        List<String> frameworkAnnotatedMethods = extractFrameworkAnnotatedMethods(masked, language);
        List<DbAccess> dbAccesses = extractDbAccesses(masked, language);
        String extendedClass = extractExtendedClass(masked, language);
        List<String> transactionalMethods = extractTransactionalMethods(masked, language);
        List<String> interfaceMethods = extractInterfaceMethods(masked, language, functions);
        List<String> serviceCalls = extractServiceCalls(masked, language);
        String feignClientTarget = extractFeignClientTarget(masked, language);
        // 필드 선언 타입명 — tree-sitter가 이미 수신자 해소용으로 추출한 것을 CIRCULAR_BEAN_DEPENDENCY용으로 재사용(Java만, distinct)
        List<String> fieldDependencyTypes = javaTs.isPresent()
                ? new ArrayList<>(new LinkedHashSet<>(javaTs.get().fieldTypes().values()))
                : List.of();
        String beanStereotype = extractBeanStereotype(masked, language);
        List<String> lazyDependencyTypes = extractLazyDependencyTypes(masked, language);

        return new ParsedFile(relativePath, language, functions, imports, fileComment, functionComments, functionCalls, instantiatedClasses, dbTables, repositoryEntityClass, entityColumns, apiCalls, controllerMappings, implementedInterfaces, asyncMethods, jsxComponents, rawSqlAccesses, frameworkAnnotatedMethods, valueReferencedFunctions, functionDefCounts, declaredTypes, testMethods, dbAccesses, extendedClass, controllerMappingFunctions, transactionalMethods, functionLines, functionColumns, interfaceMethods, serviceCalls, feignClientTarget, fieldDependencyTypes, beanStereotype, lazyDependencyTypes);
    }

    // 주석 본문을 공백으로 치환한 길이 보존 사본 생성 — 식별자 검출기가 주석 속 식별자를 코드로 오인하지 않게 함
    // 문자열 내부의 // · # · /* 는 주석으로 오인하면 안 되므로 문자열을 추적해 건너뛴다. 문자열 본문은 Stage 1에서 보존.
    private String maskComments(String content, String language) {
        boolean lineSlash = !language.equals("Python") && !language.equals("Ruby");
        boolean lineHash = language.equals("Python") || language.equals("Ruby") || language.equals("PHP");
        boolean blockSlashStar = !language.equals("Python") && !language.equals("Ruby");
        boolean singleQuote = !language.equals("Rust"); // Rust 의 ' 은 lifetime 표기라 문자열로 보지 않음
        boolean backtick = language.equals("TypeScript") || language.equals("JavaScript") || language.equals("Go");
        boolean tripleQuote = language.equals("Python") || language.equals("Java")
                || language.equals("Kotlin") || language.equals("Swift");

        char[] in = content.toCharArray();
        char[] out = in.clone();
        int n = in.length;
        int i = 0;
        while (i < n) {
            char c = in[i];

            // 삼중 따옴표 문자열(Python docstring·Java/Kotlin/Swift 텍스트 블록) — 내부 #·// 를 주석으로 오인 방지
            if (tripleQuote && (isTriple(in, i, '"') || (language.equals("Python") && isTriple(in, i, '\'')))) {
                char q = in[i];
                i += 3;
                while (i < n && !isTriple(in, i, q)) i++;
                i = Math.min(n, i + 3);
                continue;
            }

            // 문자열/문자 리터럴 — 본문은 보존하되 닫힐 때까지 건너뛴다
            if (c == '"' || (c == '\'' && singleQuote) || (c == '`' && backtick)) {
                char quote = c;
                i++;
                while (i < n) {
                    char d = in[i];
                    if (d == '\\' && quote != '`') { i += 2; continue; } // 이스케이프 (백틱 raw 문자열 제외)
                    if (d == quote) { i++; break; }
                    if (d == '\n' && quote != '`') break; // 비-멀티라인 문자열 미종료 방어
                    i++;
                }
                continue;
            }

            // 라인 주석 //
            if (lineSlash && c == '/' && i + 1 < n && in[i + 1] == '/') {
                while (i < n && in[i] != '\n') { out[i] = ' '; i++; }
                continue;
            }
            // 라인 주석 #
            if (lineHash && c == '#') {
                while (i < n && in[i] != '\n') { out[i] = ' '; i++; }
                continue;
            }
            // 블록 주석 /* ... */
            if (blockSlashStar && c == '/' && i + 1 < n && in[i + 1] == '*') {
                while (i < n && !(in[i] == '*' && i + 1 < n && in[i + 1] == '/')) {
                    if (in[i] != '\n') out[i] = ' ';
                    i++;
                }
                if (i + 1 < n) { out[i] = ' '; out[i + 1] = ' '; i += 2; }
                else { while (i < n) { if (in[i] != '\n') out[i] = ' '; i++; } }
                continue;
            }
            i++;
        }
        return new String(out);
    }

    // 위치 i 에서 세 글자가 모두 q 인지 (삼중 따옴표 경계)
    private boolean isTriple(char[] a, int i, char q) {
        return i + 2 < a.length && a[i] == q && a[i + 1] == q && a[i + 2] == q;
    }

    // 파일 상단 첫 번째 주석 추출
    private String extractFileComment(String content, String language) {
        String[] lines = content.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            if (language.equals("Python") || language.equals("Ruby")) {
                if (trimmed.startsWith("#")) return trimmed.substring(1).trim();
                break;
            }
            if (trimmed.startsWith("//")) return trimmed.substring(2).trim();
            if (trimmed.startsWith("/*")) {
                String comment = trimmed.replaceAll("^/\\*+\\s*", "").replaceAll("\\s*\\*+/$", "").trim();
                return comment.isEmpty() ? null : comment;
            }
            // 주석 아닌 줄이 나오면 중단
            if (!trimmed.startsWith("package") && !trimmed.startsWith("'use") && !trimmed.startsWith("#!")) break;
        }
        return null;
    }

    // 함수 바로 위 한 줄 주석 추출 (함수명 → 주석 맵) — 순방향 라인 스캔으로 멀티라인 파라미터 지원
    private Map<String, String> extractFunctionComments(String content, String language) {
        Map<String, String> result = new LinkedHashMap<>();
        String[] lines = content.split("\n");

        for (int i = 0; i < lines.length - 1; i++) {
            String line = lines[i].trim();

            // 주석 줄인지 확인
            String candidate = null;
            if (line.startsWith("//")) {
                candidate = line.substring(2).trim();
            } else if (line.startsWith("*") && !line.startsWith("*/")) {
                candidate = line.replaceAll("^\\*+\\s*", "").trim();
            } else if (line.startsWith("#") && (language.equals("Python") || language.equals("Ruby"))) {
                candidate = line.substring(1).trim();
            }
            if (candidate == null || candidate.isBlank()) continue;

            // 주석 다음으로 어노테이션/빈 줄을 건너뛰며 함수 정의 줄 탐색 (최대 10줄)
            for (int k = i + 1; k < Math.min(i + 10, lines.length); k++) {
                String ahead = lines[k].trim();
                if (ahead.isEmpty()) continue;
                if (ahead.startsWith("@")) continue;

                // 함수 정의 줄에서 이름 추출 — 식별자(영문 시작) 뒤에 '(' 가 오는 패턴
                Matcher nm = Pattern.compile("\\b([a-zA-Z_][\\w]*)\\s*\\(").matcher(ahead);
                while (nm.find()) {
                    String name = nm.group(1);
                    if (!isKeyword(name) && !result.containsKey(name)) {
                        result.put(name, candidate);
                    }
                }
                break;
            }
        }
        return result;
    }

    // content의 offset 위치까지의 줄 번호(0-based) 계산
    private int countNewlines(String content, int offset) {
        int count = 0;
        for (int i = 0; i < offset; i++) {
            if (content.charAt(i) == '\n') count++;
        }
        return count;
    }

    // 소스 코드에서 함수/메서드 이름 목록을 추출
    private List<String> extractFunctions(String content, String language) {
        Pattern pattern = getFunctionPattern(language);
        if (pattern == null) return List.of();

        List<String> result = new ArrayList<>();
        Matcher m = pattern.matcher(content);
        while (m.find()) {
            String name = extractFirstGroup(m);
            if (name != null && !isKeyword(name)) result.add(name);
        }

        // Java/Kotlin 인터페이스 추상 메서드 추가 추출 (접근 제어자 없이 세미콜론으로 끝나는 메서드)
        if ((language.equals("Java") || language.equals("Kotlin")) && isJavaInterface(content)) {
            Matcher ifaceMatcher = INTERFACE_METHOD_PATTERN.matcher(content);
            while (ifaceMatcher.find()) {
                String name = ifaceMatcher.group(1);
                if (name != null && !isKeyword(name) && !result.contains(name)) {
                    result.add(name);
                }
            }
        }

        return result;
    }

    // 인터페이스 선언 여부 확인
    private boolean isJavaInterface(String content) {
        return Pattern.compile("\\binterface\\s+\\w+").matcher(content).find();
    }

    // 접근 제어자 없이 세미콜론으로 끝나는 인터페이스 추상 메서드 패턴
    private static final Pattern INTERFACE_METHOD_PATTERN = Pattern.compile(
        "^\\s*(?:[\\w<>\\[\\]?,]+\\s+)+(\\w+)\\s*\\([^)]*\\)\\s*(?:throws[^;{]+)?;",
        Pattern.MULTILINE
    );

    // 언어별 함수 정의 정규식 패턴 반환
    private Pattern getFunctionPattern(String language) {
        return switch (language) {
            case "Java", "C#" ->
                Pattern.compile("(?:(?:public|private|protected|static|final|synchronized|abstract|default|native)\\s+)+(?:[\\w<>\\[\\]?,]+\\s+)*(\\w+)\\s*\\([^)]*\\)\\s*(?:throws[^{]+)?\\{",
                        Pattern.MULTILINE);
            // Kotlin: public/private 등 접근 제어자 없이 fun 키워드만으로 함수 정의 가능
            case "Kotlin" ->
                Pattern.compile("^\\s*(?:(?:public|private|protected|internal|override|open|abstract|suspend|inline|operator|external)\\s+)*fun\\s+(\\w+)\\s*[(<]",
                        Pattern.MULTILINE);
            // findEnclosingFunction(정규식 전용 위치 휴리스틱, tree-sitter 미사용) 전용 패턴 — 데코레이터 없이
            // methodName() {} 형태로 선언되는 class method(NestJS 등)를 4번째 대안으로 인식. isKeyword가
            // if/for/while/switch/catch 등 제어문을 걸러내 오탐을 막는다(제어문은 뒤에 '(' 없는 catch류 제외 나머지도 필터링됨).
            case "TypeScript", "JavaScript" ->
                Pattern.compile("(?:function\\s+(\\w+)|(?:const|let|var)\\s+(\\w+)\\s*=\\s*(?:async\\s*)?(?:\\([^)]*\\)|\\w+)\\s*=>|(?:async\\s+)?function\\s*\\*?\\s*(\\w+)" +
                        "|^[ \\t]*(?:(?:public|private|protected|static|readonly|abstract|override|async)\\s+)*(\\w+)\\s*\\([^)]*\\)\\s*(?::\\s*[^{;=]+)?\\s*\\{)",
                        Pattern.MULTILINE);
            case "Python" ->
                Pattern.compile("^\\s*(?:async\\s+)?def\\s+(\\w+)\\s*\\(", Pattern.MULTILINE);
            // Go: 리시버는 (변수 타입)·(타입)·(*타입) 모두 허용 — 타입 전용 리시버 func (jsonBinding) Bind() 도 인식
            case "Go" ->
                Pattern.compile("^func\\s+(?:\\(\\s*\\*?\\w+(?:\\s+\\*?\\w+)?\\s*\\)\\s+)?(\\w+)\\s*\\(", Pattern.MULTILINE);
            case "Rust" ->
                Pattern.compile("^\\s*(?:pub\\s+)?(?:async\\s+)?fn\\s+(\\w+)\\s*\\(", Pattern.MULTILINE);
            case "Ruby" ->
                Pattern.compile("^\\s*def\\s+(\\w+[?!]?)", Pattern.MULTILINE);
            case "PHP" ->
                Pattern.compile("^\\s*(?:(?:public|private|protected|static|abstract|final)\\s+)*function\\s+(\\w+)\\s*\\(",
                        Pattern.MULTILINE);
            case "Swift" ->
                Pattern.compile("^\\s*(?:(?:public|private|internal|open|fileprivate|static|override|class)\\s+)*func\\s+(\\w+)\\s*[(<]",
                        Pattern.MULTILINE);
            default -> null;
        };
    }

    // 정규식 매칭 결과에서 첫 번째 유효 캡처 그룹 반환
    private String extractFirstGroup(Matcher m) {
        for (int i = 1; i <= m.groupCount(); i++) {
            if (m.group(i) != null && !m.group(i).isBlank()) return m.group(i);
        }
        return null;
    }

    // 소스 코드에서 import 경로 목록을 추출
    private List<String> extractImports(String content, String language) {
        // Go는 import 문/블록 내부의 패키지 경로만 추출 — 임의 문자열 리터럴("uri","query" 등) 오인 차단
        if ("Go".equals(language)) return extractGoImports(content);

        Pattern pattern = switch (language) {
            case "Java" -> Pattern.compile("^import\\s+([\\w.]+);", Pattern.MULTILINE);
            case "Kotlin" -> Pattern.compile("^import\\s+([\\w.]+)", Pattern.MULTILINE);
            case "TypeScript", "JavaScript" ->
                Pattern.compile("from\\s+['\"]([^'\"]+)['\"]", Pattern.MULTILINE);
            case "Python" ->
                Pattern.compile("^(?:from\\s+([\\w.]+)\\s+import|import\\s+([\\w.,\\s]+))", Pattern.MULTILINE);
            case "Rust" -> Pattern.compile("^\\s*use\\s+([\\w:]+)", Pattern.MULTILINE);
            case "C#" -> Pattern.compile("^using\\s+([\\w.]+);", Pattern.MULTILINE);
            case "Ruby" -> Pattern.compile("^\\s*require(?:_relative)?\\s+['\"]([^'\"]+)['\"]", Pattern.MULTILINE);
            case "PHP" -> Pattern.compile("^\\s*(?:use|namespace)\\s+([\\w\\\\]+)", Pattern.MULTILINE);
            case "Swift" -> Pattern.compile("^import\\s+(\\w+)", Pattern.MULTILINE);
            default -> null;
        };

        if (pattern == null) return List.of();
        List<String> result = new ArrayList<>();
        Matcher m = pattern.matcher(content);
        while (m.find()) {
            for (int i = 1; i <= m.groupCount(); i++) {
                if (m.group(i) != null && !m.group(i).isBlank()) {
                    result.add(m.group(i).trim());
                    break;
                }
            }
        }
        return result;
    }

    private static final Pattern GO_IMPORT_BLOCK = Pattern.compile("(?s)^import\\s*\\((.*?)\\)", Pattern.MULTILINE);
    private static final Pattern GO_IMPORT_SINGLE = Pattern.compile("^import\\s+(?:[\\w.]+\\s+|_\\s+)?\"([^\"]+)\"", Pattern.MULTILINE);
    private static final Pattern GO_QUOTED_PATH = Pattern.compile("\"([^\"]+)\"");

    // Go import 경로 추출 — import 블록·단일 import 문 안의 패키지 경로만 (별칭/blank import 포함)
    private List<String> extractGoImports(String content) {
        List<String> result = new ArrayList<>();
        Matcher block = GO_IMPORT_BLOCK.matcher(content);
        while (block.find()) {
            Matcher path = GO_QUOTED_PATH.matcher(block.group(1));
            while (path.find()) result.add(path.group(1).trim());
        }
        Matcher single = GO_IMPORT_SINGLE.matcher(content);
        while (single.find()) result.add(single.group(1).trim());
        return result;
    }

    // 각 함수 본문에서 호출하는 함수명 목록 추출 — 함수명(파라미터) 패턴 스캔
    private Map<String, List<String>> extractFunctionCalls(String content, String language, List<String> definedFunctions) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        if (definedFunctions.isEmpty()) return result;

        // 함수 정의 경계를 찾기 위한 패턴
        Pattern funcDefPattern = getFunctionPattern(language);
        if (funcDefPattern == null) return result;

        // C#/Go는 메서드명이 PascalCase 관례 — 대문자 시작 호출도 인식 (new 인스턴스화는 제외)
        boolean pascalMethods = "C#".equals(language) || "Go".equals(language);
        // 함수 호출 패턴: 식별자 뒤에 '(' — 키워드 제외. 소문자 언어는 생성자(대문자) 제외, C#/Go는 대문자 허용
        Pattern callPattern = pascalMethods
                ? Pattern.compile("(?<!new\\s)\\b([A-Za-z_][a-zA-Z0-9_]*)\\s*\\(")
                : Pattern.compile("\\b([a-z][a-zA-Z0-9_]*)\\s*\\(");
        // 정적/한정 호출: Receiver.method() — C#/Go는 메서드명도 PascalCase 허용
        Pattern qualifiedCallPattern = pascalMethods
                ? Pattern.compile("\\b([A-Z][a-zA-Z0-9_]*)\\.([A-Za-z_][a-zA-Z0-9_]*)\\s*\\(")
                : Pattern.compile("\\b([A-Z][a-zA-Z0-9_]*)\\.([a-z][a-zA-Z0-9_]*)\\s*\\(");

        Matcher defMatcher = funcDefPattern.matcher(content);
        List<int[]> funcBoundaries = new ArrayList<>(); // [nameGroupStart, bodyStart]
        List<String> funcOrder = new ArrayList<>();

        while (defMatcher.find()) {
            String name = extractFirstGroup(defMatcher);
            if (name == null || isKeyword(name)) continue;
            funcOrder.add(name);
            funcBoundaries.add(new int[]{defMatcher.start(), defMatcher.end()});
        }

        for (int i = 0; i < funcOrder.size(); i++) {
            String funcName = funcOrder.get(i);
            int bodyStart = funcBoundaries.get(i)[1];
            int bodyEnd = i + 1 < funcBoundaries.size() ? funcBoundaries.get(i + 1)[0] : content.length();

            String body = content.substring(bodyStart, Math.min(bodyEnd, content.length()));
            Set<String> calls = new LinkedHashSet<>();
            // 단순 함수 호출: method()
            Matcher callMatcher = callPattern.matcher(body);
            while (callMatcher.find()) {
                String callee = callMatcher.group(1);
                if (!isKeyword(callee) && !callee.equals(funcName)) calls.add(callee);
            }
            // 정적 팩토리/클래스 메서드 호출: ClassName.method() → "ClassName::method" 형식으로 추가
            Matcher qualifiedMatcher = qualifiedCallPattern.matcher(body);
            while (qualifiedMatcher.find()) {
                String className = qualifiedMatcher.group(1);
                String methodName = qualifiedMatcher.group(2);
                if (!isKeyword(methodName)) calls.add(className + "::" + methodName);
            }
            if (!calls.isEmpty()) {
                result.put(funcName, new ArrayList<>(calls));
            }
        }
        return result;
    }

    // 함수가 값으로 참조되는 함수명 추출
    // 같은 파일에 정의된 함수가 호출(name())이 아니라 값(콜백·고차함수 인자)으로 참조되면 FUNCTION_CALL 엣지가
    // 생기지 않아 DEAD_CODE 오탐이 된다. 예: Go register(handler), JS arr.map(fn), Python sorted(x, key=fn).
    // 값 참조 = 식별자 앞에 '.' 없음(한정 접근 obj.fn 제외)·뒤에 '(' 없음(호출·정의 제외). 같은 파일 정의 함수로
    // 한정해 변수명 충돌 오검출을 억제(보수적). 오검출 시 결과는 경고 1개 미표시(과소 경고)라 안전하다.
    private List<String> extractValueReferencedFunctions(String content, List<String> definedFunctions) {
        if (definedFunctions.isEmpty()) return List.of();
        StringBuilder alt = new StringBuilder();
        for (String name : new LinkedHashSet<>(definedFunctions)) {
            if (name == null || name.isEmpty()) continue;
            if (alt.length() > 0) alt.append('|');
            alt.append(Pattern.quote(name));
        }
        if (alt.length() == 0) return List.of();
        Pattern refPattern = Pattern.compile("(?<![.\\w])(" + alt + ")\\b(?!\\s*\\()");
        Set<String> referenced = new LinkedHashSet<>();
        Matcher m = refPattern.matcher(content);
        while (m.find()) referenced.add(m.group(1));
        return new ArrayList<>(referenced);
    }

    // 파일 전체에서 new ClassName() 패턴으로 인스턴스화되는 클래스명 목록 추출
    private List<String> extractInstantiatedClasses(String content) {
        Pattern pattern = Pattern.compile("\\bnew\\s+([A-Z][\\w]*)\\s*[<(]");
        Matcher m = pattern.matcher(content);
        Set<String> result = new LinkedHashSet<>();
        while (m.find()) {
            String name = m.group(1);
            // 제네릭 컨테이너·예외·빌더 등 제외
            if (!INSTANTIATION_SKIP.contains(name)) result.add(name);
        }
        return new ArrayList<>(result);
    }

    private static final Set<String> INSTANTIATION_SKIP = Set.of(
        "ArrayList", "HashMap", "HashSet", "LinkedHashMap", "LinkedHashSet", "LinkedList",
        "TreeMap", "TreeSet", "StringBuilder", "StringBuffer",
        "Object", "Exception", "RuntimeException", "IllegalArgumentException",
        "IllegalStateException", "UnsupportedOperationException", "NullPointerException",
        "Thread", "Runnable", "Random", "Scanner", "File", "Path"
    );

    // @Entity / Prisma / TypeORM / SQLAlchemy 등에서 DB 테이블 정보 추출
    private List<DbTableInfo> extractDbTables(String content, String language, String filePath) {
        List<DbTableInfo> result = new ArrayList<>();

        // Java/Kotlin: @Entity 어노테이션이 있는 클래스
        if ((language.equals("Java") || language.equals("Kotlin")) && content.contains("@Entity")) {
            String tableName = null;
            Matcher tableMatcher = Pattern.compile("@Table\\s*\\([^)]*name\\s*=\\s*[\"']([^\"']+)[\"']").matcher(content);
            if (tableMatcher.find()) tableName = tableMatcher.group(1);
            if (tableName == null) {
                Matcher classMatcher = Pattern.compile("\\bclass\\s+(\\w+)").matcher(content);
                if (classMatcher.find()) tableName = classMatcher.group(1);
            }
            if (tableName != null) {
                result.add(new DbTableInfo(tableName, extractFileNameWithoutExt(filePath)));
            }
        }

        // TypeScript/JavaScript: TypeORM @Entity() 데코레이터
        // className은 실제 클래스명을 쓴다(TypeORM 접근 Repository<Entity> 매칭용). 테이블명은 @Entity('x') 우선, 없으면 클래스명.
        if ((language.equals("TypeScript") || language.equals("JavaScript")) && content.contains("@Entity")) {
            String className = null;
            Matcher classMatcher = Pattern.compile("\\bclass\\s+(\\w+)").matcher(content);
            if (classMatcher.find()) className = classMatcher.group(1);
            if (className != null) {
                Matcher teMatcher = Pattern.compile("@Entity\\s*\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)").matcher(content);
                String tableName = teMatcher.find() ? teMatcher.group(1) : className;
                result.add(new DbTableInfo(tableName, className));
            }
        }

        // Python: SQLAlchemy 선언형 모델 — base에 Base/Model 토큰 + 본문 필드 신호(Column/mapped_column/Mapped/__tablename__).
        // ★실전은 declarative_base()의 Base뿐 아니라 Flask-SQLAlchemy db.Model(class X(Model)), 믹스인 조합(class X(SurrogatePK, Model)),
        //   프로젝트 추상베이스(class X(TimestampedBase))까지 쓴다 → 좁은 class X(Base) 정규식은 db.Model을 놓친다.
        //   필드 신호로 베이스 무관 감지(Django는 models.*Field라 신호 분리=무충돌). 순수 믹스인(SurrogatePK(object))은 base 토큰 없어 제외.
        // 마이그레이션 파일(Alembic op.create_table 등)은 클래스 본문 필드가 아니므로 무관하나, 경로 가드로 이중 안전.
        if (language.equals("Python") && !filePath.replace("\\", "/").contains("/migrations/")) {
            Pattern saFieldSignal = Pattern.compile("=\\s*(?:db\\.)?(?:Column|mapped_column)\\(|\\bMapped\\[|\\b__tablename__\\s*=");
            Matcher m = Pattern.compile("^class\\s+(\\w+)\\s*\\(([^)]*)\\)\\s*:", Pattern.MULTILINE).matcher(content);
            while (m.find()) {
                String className = m.group(1);
                String bases = m.group(2);
                if (!bases.contains("Base") && !bases.contains("Model")) continue;
                String block = content.substring(m.end(), nextTopLevelClassIndex(content, m.end()));
                if (!saFieldSignal.matcher(block).find()) continue;
                // 추상 베이스(__abstract__ = True)는 테이블 아님
                if (Pattern.compile("\\b__abstract__\\s*=\\s*True\\b").matcher(block).find()) continue;
                Matcher tnMatcher = Pattern.compile("\\b__tablename__\\s*=\\s*['\"]([^'\"]+)['\"]").matcher(block);
                String tableName = tnMatcher.find() ? tnMatcher.group(1) : className;
                result.add(new DbTableInfo(tableName, className));
            }
        }

        // Python: Django ORM — models.Model 직접 상속 OR 본문에 models 필드 존재(추상 베이스 상속 패턴 포함). 추상 모델 제외, Meta.db_table 우선.
        // ★실전 Django는 class Article(TimestampedModel) 처럼 프로젝트 추상 베이스를 상속해 직접 상속만 보면 대부분 놓친다.
        //   본문의 models.*Field/ForeignKey(모델 필드 신호)로 베이스 무관하게 잡는다(SQLAlchemy는 Column 사용이라 무충돌).
        // 마이그레이션 파일은 migrations.CreateModel(fields=[('x', models.CharField())]) 안에 models.*Field 가 들어
        // 모델로 오탐된다 → migrations/ 경로 전체 제외(스키마 정의일 뿐 모델 아님).
        if (language.equals("Python") && !filePath.replace("\\", "/").contains("/migrations/")) {
            Pattern fieldSignal = Pattern.compile("\\bmodels\\.(?:\\w*Field|ForeignKey)\\b");
            Matcher m = Pattern.compile("^class\\s+(\\w+)\\s*\\(([^)]*)\\)\\s*:", Pattern.MULTILINE).matcher(content);
            while (m.find()) {
                String className = m.group(1);
                String bases = m.group(2);
                // migrations.Migration 서브클래스는 모델이 아님(경로 가드의 이중 안전망)
                if (bases.contains("migrations.Migration")) continue;
                String block = content.substring(m.end(), nextTopLevelClassIndex(content, m.end()));
                boolean isDjangoModel = bases.contains("models.Model") || fieldSignal.matcher(block).find();
                if (!isDjangoModel) continue;
                if (Pattern.compile("\\babstract\\s*=\\s*True\\b").matcher(block).find()) continue;
                Matcher dbt = Pattern.compile("\\bdb_table\\s*=\\s*['\"]([^'\"]+)['\"]").matcher(block);
                String tableName = dbt.find() ? dbt.group(1) : className.toLowerCase();
                result.add(new DbTableInfo(tableName, className));
            }
        }

        // Prisma 스키마 파일: model 블록 (멀티 파일 스키마 지원 — 모든 .prisma 대상)
        if (filePath.endsWith(".prisma")) {
            Matcher m = Pattern.compile("^model\\s+(\\w+)\\s*\\{", Pattern.MULTILINE).matcher(content);
            while (m.find()) {
                result.add(new DbTableInfo(m.group(1), m.group(1)));
            }
        }

        // Ruby: ActiveRecord (class X < ApplicationRecord)
        if (language.equals("Ruby")) {
            Matcher m = Pattern.compile("^class\\s+(\\w+)\\s*<\\s*(?:ApplicationRecord|ActiveRecord::Base)", Pattern.MULTILINE).matcher(content);
            while (m.find()) {
                result.add(new DbTableInfo(toSnakeCase(m.group(1)) + "s", m.group(1)));
            }
        }

        // PHP: Eloquent (class X extends Model)
        if (language.equals("PHP")) {
            Matcher m = Pattern.compile("^class\\s+(\\w+)\\s+extends\\s+(?:Model|Eloquent)", Pattern.MULTILINE).matcher(content);
            while (m.find()) {
                result.add(new DbTableInfo(toSnakeCase(m.group(1)) + "s", m.group(1)));
            }
        }

        // C#: Entity Framework DbContext (public DbSet<EntityName> TableName { get; set; })
        if (language.equals("C#")) {
            Matcher m = Pattern.compile("public\\s+DbSet\\s*<\\s*(\\w+)\\s*>\\s+(\\w+)\\s*\\{", Pattern.MULTILINE).matcher(content);
            while (m.find()) {
                result.add(new DbTableInfo(m.group(2), m.group(1))); // 프로퍼티명을 테이블명으로
            }
            // [Table("name")] 어노테이션이 있는 클래스
            Matcher tableMatcher = Pattern.compile("\\[Table\\s*\\(\\s*\"([^\"]+)\"\\s*\\)\\]").matcher(content);
            if (tableMatcher.find()) {
                String tableName = tableMatcher.group(1);
                Matcher classMatcher = Pattern.compile("\\bclass\\s+(\\w+)").matcher(content);
                if (classMatcher.find()) result.add(new DbTableInfo(tableName, classMatcher.group(1)));
            }
        }

        // Swift: Core Data (@objc(EntityName) class X: NSManagedObject)
        if (language.equals("Swift")) {
            Matcher m = Pattern.compile("@objc\\(\"(\\w+)\"\\)\\s*\\nclass\\s+(\\w+)\\s*:\\s*NSManagedObject", Pattern.MULTILINE).matcher(content);
            while (m.find()) {
                result.add(new DbTableInfo(m.group(1), m.group(2)));
            }
            // @objcMembers class X: NSManagedObject 패턴 폴백
            Matcher fallback = Pattern.compile("class\\s+(\\w+)\\s*:\\s*NSManagedObject").matcher(content);
            while (fallback.find()) {
                result.add(new DbTableInfo(toSnakeCase(fallback.group(1)), fallback.group(1)));
            }
        }

        // Go: GORM (gorm.Model 임베딩 / TableName() 오버라이드) + Beego ORM (orm.RegisterModel)
        if (language.equals("Go")) {
            Map<String, String> goTables = new LinkedHashMap<>();
            // type User struct { gorm.Model ... } — GORM 규칙: snake_case 복수형
            Matcher gm = Pattern.compile("type\\s+(\\w+)\\s+struct\\s*\\{[^}]*?\\bgorm\\.Model\\b").matcher(content);
            while (gm.find()) {
                goTables.put(gm.group(1), toSnakeCase(gm.group(1)) + "s");
            }
            // Beego: orm.RegisterModel(new(User), new(Profile)) — snake_case 단수형
            Matcher bm = Pattern.compile("orm\\.RegisterModel\\s*\\(((?:\\s*new\\s*\\(\\s*\\w+\\s*\\)\\s*,?)+)\\s*\\)").matcher(content);
            while (bm.find()) {
                Matcher nm = Pattern.compile("new\\s*\\(\\s*(\\w+)\\s*\\)").matcher(bm.group(1));
                while (nm.find()) {
                    goTables.putIfAbsent(nm.group(1), toSnakeCase(nm.group(1)));
                }
            }
            // func (u *User) TableName() string { return "users" } — 명시적 테이블명이 규칙 기반 이름을 덮어씀
            Matcher tm = Pattern.compile("func\\s*\\(\\s*(?:\\w+\\s+)?\\*?(\\w+)\\s*\\)\\s*TableName\\s*\\(\\s*\\)\\s*string\\s*\\{\\s*return\\s+\"([^\"]+)\"").matcher(content);
            while (tm.find()) {
                goTables.put(tm.group(1), tm.group(2));
            }
            goTables.forEach((className, tableName) -> result.add(new DbTableInfo(tableName, className)));
        }

        return result;
    }

    // Spring Cloud @FeignClient(name="customers-service") 선언에서 논리 서비스명 추출 — SERVICE_CALL_CHAIN의
    // FeignClient 지원(1차 스코프 WebClient/RestTemplate 후속, 2026-07-17). name/value 속성 우선, 없으면
    // 첫 위치 인자(바로 문자열, 예: @FeignClient("customers-service"))를 서비스명으로 — Spring이 name/value를
    // alias로 취급하는 것과 동일. GraphBuilder가 이 인터페이스를 import하는 파일을 호출자로 간주(DI 대신 IMPORT 재사용).
    private String extractFeignClientTarget(String content, String language) {
        if (!language.equals("Java") && !language.equals("Kotlin")) return null;
        Matcher m = Pattern.compile("@FeignClient\\s*\\(([^)]*)\\)").matcher(content);
        if (!m.find()) return null;
        String args = m.group(1);
        Matcher named = Pattern.compile("(?:name|value)\\s*=\\s*[\"']([^\"']+)[\"']").matcher(args);
        if (named.find()) return named.group(1);
        Matcher bare = Pattern.compile("^\\s*[\"']([^\"']+)[\"']").matcher(args);
        return bare.find() ? bare.group(1) : null;
    }

    // Spring 빈 스테레오타입 어노테이션 — CIRCULAR_BEAN_DEPENDENCY 판정용(Java/Kotlin만). @Entity 판정과 동일하게
    // 파일당 클래스 1개 관례에 기대 어노테이션-클래스 인접 여부는 보지 않고 파일 내 존재만 확인.
    private static final List<String> BEAN_STEREOTYPE_ANNOTATIONS = List.of(
            "Component", "Service", "Repository", "Configuration", "RestController");

    private String extractBeanStereotype(String content, String language) {
        if (!language.equals("Java") && !language.equals("Kotlin")) return null;
        for (String ann : BEAN_STEREOTYPE_ANNOTATIONS) {
            if (Pattern.compile("@" + ann + "\\b").matcher(content).find()) return ann;
        }
        return null;
    }

    // 생성자 파라미터의 @Lazy 어노테이션이 붙은 타입명 목록 — CIRCULAR_BEAN_DEPENDENCY가 순환 판정에서 제외할
    // 의존 식별용(@Lazy 프록시 주입은 즉시 완전 생성을 요구하지 않아 Spring이 실제로 순환을 허용하는 지점).
    // 파라미터 형태(@Lazy Type name)만 보고 생성자/세터 위치는 구분하지 않음 — @Lazy는 실질적으로 생성자 주입 전용.
    private List<String> extractLazyDependencyTypes(String content, String language) {
        if (!language.equals("Java") && !language.equals("Kotlin")) return List.of();
        List<String> result = new ArrayList<>();
        Matcher m = Pattern.compile("@Lazy\\s+(?:final\\s+)?([A-Z]\\w*)\\s+\\w").matcher(content);
        while (m.find()) result.add(m.group(1));
        return result;
    }

    // JPA Repository 인터페이스 확장에서 엔티티 클래스명 추출
    private String extractRepositoryEntityClass(String content, String language) {
        if (!language.equals("Java") && !language.equals("Kotlin")) return null;
        // extends JpaRepository<EntityName, ID> 또는 CrudRepository, PagingAndSortingRepository
        Matcher m = Pattern.compile("extends\\s+(?:Jpa|Crud|PagingAndSorting)?Repository\\s*<\\s*(\\w+)\\s*,").matcher(content);
        return m.find() ? m.group(1) : null;
    }

    // "class Foo implements Bar, Baz" 패턴에서 구현 인터페이스명 목록 추출
    private List<String> extractImplementedInterfaces(String content, String language) {
        if (!language.equals("Java") && !language.equals("Kotlin")
                && !language.equals("TypeScript") && !language.equals("JavaScript")) {
            return List.of();
        }
        Matcher m = Pattern.compile(
                "\\bclass\\s+\\w[\\w<>]*(?:\\s+extends\\s+[\\w<>,.\\s]+)?\\s+implements\\s+([\\w<>,.\\s]+?)\\s*\\{"
        ).matcher(content);
        if (m.find()) {
            List<String> result = new ArrayList<>();
            for (String part : m.group(1).split(",")) {
                String name = part.trim().replaceAll("<[^>]*>", "").trim();
                if (!name.isEmpty()) result.add(name);
            }
            return result;
        }
        // ★도그푸딩 실측(2026-07-14): Spring Data Repository 인터페이스가 JpaRepository<...>와 도메인 포트
        // 인터페이스를 함께 extends해 프록시로 한 번에 구현하는 패턴(예: FooJpaRepository extends
        // JpaRepository<X,ID>, FooRepository) — class implements가 아니라 interface extends라 위 정규식에
        // 안 잡혀 BROKEN_INTERFACE_CHAIN이 도메인 포트 메서드 전체를 오탐했다.
        if (language.equals("Java") || language.equals("Kotlin")) {
            Matcher im = Pattern.compile(
                    "\\binterface\\s+\\w[\\w<>]*\\s+extends\\s+([\\w<>,.\\s]+?)\\s*\\{"
            ).matcher(content);
            if (im.find()) {
                List<String> result = new ArrayList<>();
                for (String part : im.group(1).split(",")) {
                    String name = part.trim().replaceAll("<[^>]*>", "").trim();
                    // 완전한정 이름(com.example.FooRepository)이면 단순 이름만 취한다 — ifaceNameToFile이
                    // 파일명(단순 이름) 기준으로 매칭하므로 패키지 경로가 붙으면 매칭이 안 된다(도그푸딩 실측).
                    int lastDot = name.lastIndexOf('.');
                    if (lastDot >= 0) name = name.substring(lastDot + 1);
                    // 제네릭 프레임워크 타입 자체(JpaRepository 등)는 "구현 대상 도메인 인터페이스"가 아니므로 제외
                    if (name.isEmpty() || name.matches("(?:Jpa|Crud|PagingAndSorting)?Repository")) continue;
                    result.add(name);
                }
                return result;
            }
        }
        return List.of();
    }

    // "class Foo extends Bar"에서 상위 클래스명 추출 — 상속 메서드 호출 해소용(엣지 정확도 패턴 A', Java 한정).
    // 제네릭 타입 파라미터 경계(class Foo<T extends Bound>)와 실제 상속을 구분하려고 "class 이름(<...>)? extends"
    // 형태만 매칭한다 — extends 직후 리터럴이 없으면(제네릭 바운드뿐이면) 매치 자체가 실패해 null을 반환한다.
    // Repository 인터페이스 상속(extractRepositoryEntityClass)은 "interface"가 아닌 "class" 키워드 요구로 무충돌.
    private String extractExtendedClass(String content, String language) {
        if (!language.equals("Java")) return null;
        Matcher m = Pattern.compile("\\bclass\\s+\\w+(?:<[^{]*?>)?\\s+extends\\s+(\\w+)").matcher(content);
        return m.find() ? m.group(1) : null;
    }

    // @Entity 클래스에서 private 필드를 칼럼 정보로 추출 — @Column(name=) 우선, 없으면 snake_case 변환
    private List<ColumnInfo> extractEntityColumns(String content, String language) {
        if ((!language.equals("Java") && !language.equals("Kotlin")) || !content.contains("@Entity")) {
            return List.of();
        }
        List<ColumnInfo> result = new ArrayList<>();
        String[] lines = content.split("\n");
        String pendingColumnName = null;
        boolean pendingConverter = false;

        for (String line : lines) {
            String t = line.trim();
            if (t.startsWith("@Column")) {
                Matcher m = Pattern.compile("name\\s*=\\s*[\"']([^\"']+)[\"']").matcher(t);
                pendingColumnName = m.find() ? m.group(1) : "";
            } else if (t.startsWith("@Convert")) {
                pendingConverter = true;
            } else if (t.startsWith("private ")) {
                // private <type> <name>; — 제네릭 타입 포함
                Matcher m = Pattern.compile("private\\s+([\\w<>\\[\\]?,\\s]+?)\\s+(\\w+)\\s*;").matcher(t);
                if (m.find()) {
                    String javaType = m.group(1).trim();
                    String fieldName = m.group(2);
                    if (!fieldName.equals("serialVersionUID") && !javaType.contains("static")) {
                        String colName = (pendingColumnName != null && !pendingColumnName.isEmpty())
                                ? pendingColumnName
                                : toSnakeCase(fieldName);
                        result.add(new ColumnInfo(fieldName, colName, javaType, pendingConverter));
                    }
                }
                pendingColumnName = null;
                pendingConverter = false;
            } else if (!t.startsWith("@") && !t.isEmpty()) {
                pendingColumnName = null;
                pendingConverter = false;
            }
        }
        return result;
    }

    // 파이썬 최상위(class Meta 등 들여쓰기 제외) class 선언 사이의 블록 끝 인덱스 — 없으면 EOF
    private int nextTopLevelClassIndex(String content, int from) {
        Matcher m = Pattern.compile("^class\\s+\\w+", Pattern.MULTILINE).matcher(content);
        return m.find(from) ? m.start() : content.length();
    }

    // camelCase → snake_case 변환
    private String toSnakeCase(String camel) {
        return camel.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }

    // 파일 경로에서 확장자 제거 후 파일명만 추출
    private String extractFileNameWithoutExt(String filePath) {
        int slash = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
        String name = slash >= 0 ? filePath.substring(slash + 1) : filePath;
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    // TypeScript/JS 파일에서 axios·fetch HTTP 호출 경로 추출 ("METHOD:/path" 형식)
    private List<String> extractApiCalls(String content, String language) {
        if (!language.equals("TypeScript") && !language.equals("JavaScript")) return List.of();
        List<String> result = new ArrayList<>();
        // axios.get('/api/...'), api.post(`/api/${id}/...`), apiClient.delete('/api/...') 등
        Pattern p = Pattern.compile(
            "\\.(get|post|put|delete|patch)\\s*\\(\\s*['\"`]([^'\"`\\n]+)['\"`]",
            Pattern.CASE_INSENSITIVE
        );
        Matcher m = p.matcher(content);
        while (m.find()) {
            String method = m.group(1).toUpperCase();
            // 템플릿 리터럴의 ${...} 부분을 * 로 정규화
            String path = m.group(2).replaceAll("\\$\\{[^}]+}", "*");
            if (path.startsWith("/")) {
                result.add(method + ":" + path);
            }
        }

        // fetch('/api/...', { method: 'POST' }) — 메서드가 URL이 아닌 옵션 객체에 있고, 없으면 GET
        Pattern fetchPattern = Pattern.compile("\\bfetch\\s*\\(\\s*['\"`]([^'\"`\\n]+)['\"`]");
        Pattern methodOption = Pattern.compile("method\\s*:\\s*['\"`](\\w+)['\"`]", Pattern.CASE_INSENSITIVE);
        Matcher fm = fetchPattern.matcher(content);
        while (fm.find()) {
            String path = fm.group(1).replaceAll("\\$\\{[^}]+}", "*");
            if (!path.startsWith("/")) continue;
            // method 옵션 탐색 범위: 현재 호출 이후 ~ 문장 끝(;) 또는 다음 fetch 전까지 (다음 호출의 옵션 오인 방지)
            String window = content.substring(fm.end(), Math.min(fm.end() + 300, content.length()));
            int boundary = window.length();
            int semicolon = window.indexOf(';');
            if (semicolon >= 0) boundary = Math.min(boundary, semicolon);
            int nextFetch = window.indexOf("fetch(");
            if (nextFetch >= 0) boundary = Math.min(boundary, nextFetch);
            Matcher om = methodOption.matcher(window.substring(0, boundary));
            String method = om.find() ? om.group(1).toUpperCase() : "GET";
            result.add(method + ":" + path);
        }
        return result;
    }

    // 모노레포 서비스 간 동기 HTTP 호출에서 대상 서비스 논리명만 추출(Java WebClient/RestTemplate,
    // Python requests, JS/TS axios). 정확한 엔드포인트 경로는 이 규칙(서비스 호출 체인 깊이)에 불필요해
    // 서비스명만 뽑는다 — 메서드 체이닝이 여러 줄에 걸쳐도(.get()\n.uri(...)) 안전하게 매칭되도록
    // uri()/RestTemplate 메서드 자체만 앵커로 쓴다. host(서비스명)가 리터럴로 직접 나타나는 컨벤션에만
    // 의존 — path에 변수가 섞여도(JS 템플릿 리터럴 `${id}`, Python f-string `{id}`) host 앞부분(`http://서비스명`)이
    // 리터럴이면 매칭. host 자체가 변수 조합(`http://{service}/...`)인 경우는 후속 스코프
    // (decisions/DECISIONS_ANALYSIS.md 참조), FeignClient는 별도 필드(feignClientTarget)로 처리.
    private List<String> extractServiceCalls(String content, String language) {
        Pattern p;
        if (language.equals("Java") || language.equals("Kotlin")) {
            p = Pattern.compile(
                "\\.(?:uri|getForObject|getForEntity|postForObject|postForEntity|put|delete|exchange)\\s*"
                    + "\\(\\s*[\"']http://([a-zA-Z0-9_-]+)"
            );
        } else if (language.equals("Python")) {
            // 리터럴 http:// 호스트 외에, docker-compose.yml environment 블록으로 주입되는
            // os.environ['VAR']/os.environ.get('VAR')/os.getenv('VAR') 기반 호출도 인식 —
            // JS/TS의 "ENV:" 표시와 동일한 방식으로 GraphBuilder가 역해소한다(SERVICE_CALL_CHAIN
            // "변수 조합 URL" ② Python 확장, decisions/DECISIONS_ANALYSIS.md 참조).
            List<String> pyResult = new ArrayList<>();
            Matcher literal = Pattern.compile(
                "\\brequests\\.(?:get|post|put|delete|patch|head|options)\\s*\\(\\s*f?[\"']http://([a-zA-Z0-9_-]+)"
            ).matcher(content);
            while (literal.find()) pyResult.add(literal.group(1));
            Matcher envVar = Pattern.compile(
                "\\brequests\\.(?:get|post|put|delete|patch|head|options)\\s*\\(\\s*f[\"']http://\\{os\\."
                    + "(?:environ\\[['\"]|environ\\.get\\(['\"]|getenv\\(['\"])([A-Za-z0-9_]+)"
            ).matcher(content);
            while (envVar.find()) pyResult.add("ENV:" + envVar.group(1));
            return pyResult.stream().distinct().toList();
        } else if (language.equals("JavaScript") || language.equals("TypeScript")) {
            // 리터럴 http:// 호스트 외에, docker-compose.yml environment 블록으로 주입되는
            // process.env.VARNAME 기반 호출도 인식 — "ENV:" 접두사로 표시해 GraphBuilder가
            // docker-compose 파싱 결과로 실제 서비스명을 역해소하게 한다(엣지 정확도 4차 감사
            // 후속 "변수 조합 URL" ②, decisions/DECISIONS_ANALYSIS.md 참조).
            List<String> jsResult = new ArrayList<>();
            Matcher literal = Pattern.compile(
                "\\baxios\\.(?:get|post|put|delete|patch)\\s*\\(\\s*[`\"']http://([a-zA-Z0-9_-]+)"
            ).matcher(content);
            while (literal.find()) jsResult.add(literal.group(1));
            Matcher envVar = Pattern.compile(
                "\\baxios\\.(?:get|post|put|delete|patch)\\s*\\(\\s*[`\"']?\\$?\\{?\\s*process\\.env\\.([A-Za-z0-9_]+)"
            ).matcher(content);
            while (envVar.find()) jsResult.add("ENV:" + envVar.group(1));
            return jsResult.stream().distinct().toList();
        } else if (language.equals("Go")) {
            // 표준 라이브러리 net/http만 1차 스코프 — http.Get/Post/Head(top-level 함수)와
            // http.NewRequest(method, url, body)+Client.Do 패턴(PUT/DELETE 등 그 외 메서드는 보통 이 경로)
            List<String> goResult = new ArrayList<>();
            Matcher topLevel = Pattern.compile(
                "\\bhttp\\.(?:Get|Post|Head)\\s*\\(\\s*\"http://([a-zA-Z0-9_-]+)"
            ).matcher(content);
            while (topLevel.find()) goResult.add(topLevel.group(1));
            Matcher newRequest = Pattern.compile(
                "\\bhttp\\.NewRequest\\s*\\(\\s*\"[A-Z]+\"\\s*,\\s*\"http://([a-zA-Z0-9_-]+)"
            ).matcher(content);
            while (newRequest.find()) goResult.add(newRequest.group(1));
            return goResult.stream().distinct().toList();
        } else {
            return List.of();
        }
        Matcher m = p.matcher(content);
        List<String> result = new ArrayList<>();
        while (m.find()) result.add(m.group(1));
        return result.stream().distinct().toList();
    }

    // docker-compose.yml의 environment 블록에서 ENV_VAR=http://service 또는 ENV_VAR: http://service 패턴을 추출
    // — 리스트 스타일("- KEY=value")과 맵 스타일("KEY: value") 둘 다 지원. 어느 서비스 소속인지는 구분하지 않고
    // 파일 전체에서 평면적으로 수집(변수명 충돌은 드물고, 실사용에서 굳이 서비스 스코프를 나눌 필요가 없어 단순화).
    private Map<String, String> extractComposeEnvHosts(String content) {
        Map<String, String> result = new LinkedHashMap<>();
        Matcher listStyle = Pattern.compile(
            "-\\s*([A-Za-z0-9_]+)=http://([a-zA-Z0-9_-]+)"
        ).matcher(content);
        while (listStyle.find()) result.put(listStyle.group(1), listStyle.group(2));
        Matcher mapStyle = Pattern.compile(
            "(?m)^\\s*([A-Za-z0-9_]+):\\s*[\"']?http://([a-zA-Z0-9_-]+)"
        ).matcher(content);
        while (mapStyle.find()) result.put(mapStyle.group(1), mapStyle.group(2));
        return result;
    }

    // 언어별 API 엔드포인트 경로 목록 추출 (Spring/Express/FastAPI/Flask/Gin 지원)
    // 클래스 레벨 @RequestMapping prefix + 메서드 레벨 suffix 합성 지원
    private List<String> extractControllerMappings(String content, String language) {
        List<String> result = new ArrayList<>();

        if (language.equals("Java") || language.equals("Kotlin")) {
            // 1단계: 클래스 레벨 @RequestMapping prefix 추출 (파일 내 첫 번째 값)
            String classPrefix = "";
            Matcher cm = Pattern.compile(
                "@RequestMapping\\s*\\(\\s*(?:value\\s*=\\s*)?[\"']([^\"']+)[\"']"
            ).matcher(content);
            if (cm.find()) classPrefix = cm.group(1);

            // 2단계: 메서드 레벨 어노테이션 추출 + prefix 합성
            Matcher mm = Pattern.compile(
                "@(?:Get|Post|Put|Delete|Patch)Mapping(?:\\s*\\(\\s*(?:value\\s*=\\s*)?[\"']([^\"']*)[\"']\\s*\\))?",
                Pattern.CASE_INSENSITIVE
            ).matcher(content);
            while (mm.find()) {
                String methodPath = mm.group(1);
                if (methodPath == null) {
                    if (!classPrefix.isEmpty()) result.add(classPrefix);
                } else {
                    String full = classPrefix + methodPath;
                    if (!full.isEmpty()) result.add(full);
                }
            }
            if (result.isEmpty() && !classPrefix.isEmpty()) result.add(classPrefix);

            // Ktor: get("/path") { ... } / post("/path") { ... } — 함수 호출 형태
            if (language.equals("Kotlin") && result.isEmpty()) {
                Matcher km = Pattern.compile(
                    "\\b(get|post|put|delete|patch)\\s*\\(\\s*\"(/[^\"\\n]*)\"",
                    Pattern.CASE_INSENSITIVE
                ).matcher(content);
                while (km.find()) {
                    result.add(km.group(1).toUpperCase() + ":" + km.group(2));
                }
            }

        } else if (language.equals("JavaScript") || language.equals("TypeScript")) {
            // Express.js: router.get('/path', ...) / app.post('/path', ...)
            Matcher m = Pattern.compile(
                "\\b(?:router|app)\\.(get|post|put|delete|patch)\\s*\\(\\s*['\"`]([^'\"`\\n]+)['\"`]",
                Pattern.CASE_INSENSITIVE
            ).matcher(content);
            while (m.find()) {
                result.add(m.group(1).toUpperCase() + ":" + m.group(2));
            }

            // NestJS: @Controller('prefix') 클래스 prefix + @Get('sub')/@Post() 메서드 데코레이터 합성.
            // @Controller가 있는 파일에 한정해 Express·일반 데코레이터 오매칭 차단
            if (content.contains("@Controller")) {
                String nestPrefix = "";
                Matcher cm = Pattern.compile("@Controller\\s*\\(\\s*['\"`]([^'\"`]*)['\"`]").matcher(content);
                if (cm.find()) nestPrefix = cm.group(1);
                Matcher nm = Pattern.compile(
                    "@(Get|Post|Put|Delete|Patch)\\s*\\(\\s*(?:['\"`]([^'\"`]*)['\"`])?\\s*\\)"
                ).matcher(content);
                while (nm.find()) {
                    String sub = nm.group(2);
                    String full = (sub != null && !sub.isEmpty())
                            ? (nestPrefix.isEmpty() ? sub : nestPrefix + "/" + sub)
                            : nestPrefix;
                    full = ("/" + full).replaceAll("/+", "/");
                    result.add(nm.group(1).toUpperCase() + ":" + full);
                }
            }

        } else if (language.equals("Python")) {
            // FastAPI/Flask: @app.get('/path') / @router.post('/path')
            Matcher m = Pattern.compile(
                "@(?:app|router|bp|blueprint)\\.(get|post|put|delete|patch)\\s*\\(\\s*['\"`]([^'\"`\\n]+)['\"`]",
                Pattern.CASE_INSENSITIVE
            ).matcher(content);
            while (m.find()) {
                result.add(m.group(1).toUpperCase() + ":" + m.group(2));
            }

            // Django: path('route/', view) / re_path(r'^route$', view) — urls.py.
            // HTTP 메서드는 뷰에서 결정되므로 urls.py만으로는 알 수 없어 GET 기본(기존 메서드 불명→GET 관례)
            Matcher dj = Pattern.compile(
                "\\b(?:re_)?path\\s*\\(\\s*r?['\"]([^'\"\\n]*)['\"]\\s*,"
            ).matcher(content);
            while (dj.find()) {
                // re_path 정규식 앵커(^ … $)는 경로가 아니므로 제거
                String path = dj.group(1).replaceAll("^\\^", "").replaceAll("\\$$", "");
                result.add("GET:" + (path.startsWith("/") ? path : "/" + path));
            }

        } else if (language.equals("Go")) {
            // Gin/Echo/Fiber: r.GET('/path', ...) / router.POST('/path', ...)
            Matcher m = Pattern.compile(
                "\\b\\w+\\.(GET|POST|PUT|DELETE|PATCH)\\s*\\(\\s*\"([^\"\\n]+)\"",
                Pattern.CASE_INSENSITIVE
            ).matcher(content);
            while (m.find()) {
                result.add(m.group(1).toUpperCase() + ":" + m.group(2));
            }

        } else if (language.equals("Ruby")) {
            // Rails routes: get '/path', post '/path'
            Matcher m = Pattern.compile(
                "^\\s*(get|post|put|delete|patch)\\s+['\"]([^'\"\\n]+)['\"]",
                Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
            ).matcher(content);
            while (m.find()) {
                result.add(m.group(1).toUpperCase() + ":" + m.group(2));
            }

        } else if (language.equals("PHP")) {
            // Laravel: Route::get('/path', ...) / $router->post('/path', ...)
            Matcher m = Pattern.compile(
                "(?:Route::|\\$router->)(get|post|put|delete|patch)\\s*\\(\\s*['\"]([^'\"\\n]+)['\"]",
                Pattern.CASE_INSENSITIVE
            ).matcher(content);
            while (m.find()) {
                result.add(m.group(1).toUpperCase() + ":" + m.group(2));
            }

        } else if (language.equals("C#")) {
            // ASP.NET Core: [HttpGet("/path")], [Route("/path")]
            // 클래스 레벨 [Route("api/[controller]")] prefix 추출
            String classPrefix = "";
            Matcher cm = Pattern.compile("\\[Route\\s*\\(\\s*\"([^\"]+)\"\\s*\\)\\]").matcher(content);
            if (cm.find()) {
                // [controller] 토큰을 실제 컨트롤러명으로 치환
                String routeTemplate = cm.group(1);
                Matcher controllerMatcher = Pattern.compile("\\bclass\\s+(\\w+)Controller\\b").matcher(content);
                if (controllerMatcher.find()) {
                    classPrefix = routeTemplate.replace("[controller]", controllerMatcher.group(1).toLowerCase());
                } else {
                    classPrefix = routeTemplate.replace("[controller]", "");
                }
            }
            Matcher mm = Pattern.compile(
                "\\[Http(Get|Post|Put|Delete|Patch)(?:\\s*\\(\\s*\"([^\"]+)\"\\s*\\))?\\]",
                Pattern.CASE_INSENSITIVE
            ).matcher(content);
            while (mm.find()) {
                String method = mm.group(1).toUpperCase();
                String path = mm.group(2) != null ? mm.group(2) : "";
                String full = classPrefix.isEmpty() ? path : (classPrefix + "/" + path).replaceAll("/+", "/");
                if (!full.isEmpty()) result.add(method + ":" + full);
                else result.add(method + ":" + classPrefix);
            }

        } else if (language.equals("Swift")) {
            // Vapor: router.get("path", use: handler) / app.get("path") { ... }
            Matcher m = Pattern.compile(
                "(?:router|app)\\.(get|post|put|delete|patch)\\s*\\(\\s*\"([^\"\\n]+)\"",
                Pattern.CASE_INSENSITIVE
            ).matcher(content);
            while (m.find()) {
                result.add(m.group(1).toUpperCase() + ":" + m.group(2));
            }
        }

        return result;
    }

    // 컨트롤러 매핑 → 실제 처리 함수명 (Java/Kotlin 1차 + JS/TS·Python·Go 2차 확장, Ruby는 제외)
    // 두 기법을 언어별로 다르게 적용: ①위치 휴리스틱(findEnclosingFunction, 데코레이터 바로 다음 함수 선언)
    // ②호출 인자 캡처(lastIdentifierArg, 라우팅 호출의 핸들러 인자를 직접 추출).
    // extractControllerMappings와 동일한 키 합성 규칙을 각 브랜치에서 반복해 키 문자열을 일치시킨다
    // (두 메서드가 상태를 공유하지 않아 중복이지만, 기존 메서드 흐름을 안 건드리는 게 더 안전하다고 판단).
    private Map<String, String> extractControllerMappingFunctions(String content, String language) {
        Map<String, String> result = new LinkedHashMap<>();

        if (language.equals("Java") || language.equals("Kotlin")) {
            String classPrefix = "";
            Matcher cm = Pattern.compile(
                "@RequestMapping\\s*\\(\\s*(?:value\\s*=\\s*)?[\"']([^\"']+)[\"']"
            ).matcher(content);
            if (cm.find()) classPrefix = cm.group(1);

            Matcher mm = Pattern.compile(
                "@(?:Get|Post|Put|Delete|Patch)Mapping(?:\\s*\\(\\s*(?:value\\s*=\\s*)?[\"']([^\"']*)[\"']\\s*\\))?",
                Pattern.CASE_INSENSITIVE
            ).matcher(content);
            while (mm.find()) {
                String methodPath = mm.group(1);
                String key = methodPath == null ? classPrefix : classPrefix + methodPath;
                if (key.isEmpty()) continue;
                String funcName = findEnclosingFunction(content, language, mm.end());
                if (funcName != null) result.put(key, funcName);
            }

        } else if (language.equals("JavaScript") || language.equals("TypeScript")) {
            // Express: router.get('/path', ...args) — 같은 줄의 마지막 인자가 순수 식별자면 핸들러로 간주.
            Matcher m = Pattern.compile(
                "\\b(?:router|app)\\.(get|post|put|delete|patch)\\s*\\(\\s*['\"`]([^'\"`\\n]+)['\"`]\\s*,([^\\n]*)",
                Pattern.CASE_INSENSITIVE
            ).matcher(content);
            while (m.find()) {
                String key = m.group(1).toUpperCase() + ":" + m.group(2);
                String handler = lastIdentifierArg(m.group(3));
                if (handler != null) result.put(key, handler);
            }

            // NestJS: @Controller('prefix') 클래스 prefix + @Get('sub')/@Post() 메서드 데코레이터 다음 class method를
            // 위치 휴리스틱(findEnclosingFunction)으로 해소 — extractControllerMappings의 동일 브랜치와 키 합성 규칙을
            // 맞춰야 GraphBuilder의 controllerMappings→controllerMappingFunctions 조회가 성립한다.
            if (content.contains("@Controller")) {
                String nestPrefix = "";
                Matcher cm = Pattern.compile("@Controller\\s*\\(\\s*['\"`]([^'\"`]*)['\"`]").matcher(content);
                if (cm.find()) nestPrefix = cm.group(1);
                Matcher nm = Pattern.compile(
                    "@(Get|Post|Put|Delete|Patch)\\s*\\(\\s*(?:['\"`]([^'\"`]*)['\"`])?\\s*\\)"
                ).matcher(content);
                while (nm.find()) {
                    String sub = nm.group(2);
                    String full = (sub != null && !sub.isEmpty())
                            ? (nestPrefix.isEmpty() ? sub : nestPrefix + "/" + sub)
                            : nestPrefix;
                    full = ("/" + full).replaceAll("/+", "/");
                    String key = nm.group(1).toUpperCase() + ":" + full;
                    String funcName = findEnclosingFunction(content, language, nm.end());
                    if (funcName != null) result.put(key, funcName);
                }
            }

        } else if (language.equals("Python")) {
            // FastAPI/Flask: 위치 휴리스틱 재사용(데코레이터 바로 다음 함수 선언)
            Matcher m = Pattern.compile(
                "@(?:app|router|bp|blueprint)\\.(get|post|put|delete|patch)\\s*\\(\\s*['\"`]([^'\"`\\n]+)['\"`]",
                Pattern.CASE_INSENSITIVE
            ).matcher(content);
            while (m.find()) {
                String key = m.group(1).toUpperCase() + ":" + m.group(2);
                String funcName = findEnclosingFunction(content, language, m.end());
                if (funcName != null) result.put(key, funcName);
            }

            // Django: path('route/', views.func) — 뷰 참조의 마지막 '.' 뒤 식별자를 핸들러로 간주.
            // views.py처럼 다른 파일에 정의된 경우가 흔한데, 그런 경우 아래 GraphBuilder의 동일 파일
            // funcNodeIds 조회가 자연히 null이 되어 엣지가 안 만들어질 뿐(확인 못하는 연결은 안 만드는 게 맞음).
            Matcher dj = Pattern.compile(
                "\\b(?:re_)?path\\s*\\(\\s*r?['\"]([^'\"\\n]*)['\"]\\s*,\\s*([\\w.]+)"
            ).matcher(content);
            while (dj.find()) {
                String path = dj.group(1).replaceAll("^\\^", "").replaceAll("\\$$", "");
                String key = "GET:" + (path.startsWith("/") ? path : "/" + path);
                String ref = dj.group(2);
                int lastDot = ref.lastIndexOf('.');
                result.put(key, lastDot >= 0 ? ref.substring(lastDot + 1) : ref);
            }

        } else if (language.equals("Go")) {
            // Gin/Echo/Fiber: r.GET("/path", handler) — 같은 줄의 마지막 인자를 핸들러로 간주(리시버 메서드
            // h.GetUsers 형태의 점(.) 포함 참조도 lastIdentifierArg가 마지막 세그먼트만 추출해 처리)
            Matcher m = Pattern.compile(
                "\\b\\w+\\.(GET|POST|PUT|DELETE|PATCH)\\s*\\(\\s*\"([^\"\\n]+)\"\\s*,([^\\n]*)",
                Pattern.CASE_INSENSITIVE
            ).matcher(content);
            while (m.find()) {
                String key = m.group(1).toUpperCase() + ":" + m.group(2);
                String handler = lastIdentifierArg(m.group(3));
                if (handler != null) result.put(key, handler);
            }
        }

        return result;
    }

    // 같은 줄의 나머지 인자 문자열에서 마지막 인자가 순수 식별자(또는 점으로 구분된 참조의 마지막 세그먼트)면
    // 반환, 익명 함수/화살표 함수가 섞여 있으면 null(오검출 방지 — 함수명이 없는 인라인 핸들러는 해소 불가)
    private String lastIdentifierArg(String restOfLine) {
        if (restOfLine.contains("=>") || restOfLine.contains("function")
                || restOfLine.contains("func(") || restOfLine.contains("{")) {
            return null;
        }
        int closeParen = restOfLine.indexOf(')');
        String argsOnly = closeParen >= 0 ? restOfLine.substring(0, closeParen) : restOfLine;
        String[] parts = argsOnly.split(",");
        String last = parts[parts.length - 1].trim();
        if (!last.matches("^[\\w$]+(?:\\.[\\w$]+)*$")) return null;
        int lastDot = last.lastIndexOf('.');
        return lastDot >= 0 ? last.substring(lastDot + 1) : last;
    }

    // 지정 위치 이후 처음 나오는 함수 선언명 반환 — 어노테이션을 다음 함수 선언에 매칭할 때 사용, 못 찾으면 null
    private String findEnclosingFunction(String content, String language, int fromPosition) {
        Pattern pattern = getFunctionPattern(language);
        if (pattern == null) return null;
        Matcher m = pattern.matcher(content);
        while (m.find()) {
            if (m.start() < fromPosition) continue;
            String name = extractFirstGroup(m);
            if (name != null && !isKeyword(name)) return name;
        }
        return null;
    }

    // 식별자가 언어 예약어인지 확인
    private boolean isKeyword(String name) {
        return Set.of("if", "else", "for", "while", "switch", "try", "catch", "return",
                "new", "class", "interface", "enum", "void", "int", "long", "boolean",
                "String", "List", "Map", "Set", "var", "val", "let", "const").contains(name);
    }

    // 리터럴이 SQL 동사로 시작해야 SQL로 인정 — 산문 중간의 키워드 오매칭 차단
    private static final Pattern RAW_SQL_ANCHOR = Pattern.compile(
        "^\\s*\\(?\\s*(SELECT|INSERT|UPDATE|DELETE)\\b", Pattern.CASE_INSENSITIVE);
    // 산문에 거의 없는 강한 SQL 구조 마커 — 하나라도 있어야 SQL로 인정
    private static final Pattern RAW_SQL_MARKER = Pattern.compile(
        "[*=]|\\?|%[sd]|:\\w|\\$\\d|;\\s*$|\\b(WHERE|JOIN|VALUES|GROUP|ORDER|LIMIT|HAVING|RETURNING|UNION)\\b",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern RAW_SQL_LITERAL = Pattern.compile("[\"'`]([^\"'`\\n]{5,500})[\"'`]");
    private static final Pattern RAW_SQL_SELECT = Pattern.compile("\\bSELECT\\b.+?\\bFROM\\s+(\\w+)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern RAW_SQL_INSERT = Pattern.compile("\\bINSERT\\s+INTO\\s+(\\w+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern RAW_SQL_UPDATE = Pattern.compile("\\bUPDATE\\s+(\\w+)\\s+SET", Pattern.CASE_INSENSITIVE);
    private static final Pattern RAW_SQL_DELETE = Pattern.compile("\\bDELETE\\s+FROM\\s+(\\w+)", Pattern.CASE_INSENSITIVE);

    // SQL 문자열 리터럴에서 테이블 접근 정보(테이블명 + READ/WRITE) 추출 — 언어 무관
    private List<RawSqlAccess> extractRawSqlAccesses(String content) {
        List<RawSqlAccess> result = new ArrayList<>();

        // 문자열 리터럴 내부의 SQL 쿼리만 스캔 — 작은따옴표·큰따옴표·백틱 문자열 탐색
        Matcher lm = RAW_SQL_LITERAL.matcher(content);
        while (lm.find()) {
            String literal = lm.group(1).trim();
            // 앵커 미통과(SQL 동사로 시작 안 함) 또는 강한 마커 없음 → 산문으로 보고 건너뜀
            Matcher am = RAW_SQL_ANCHOR.matcher(literal);
            if (!am.find()) continue;
            if (!RAW_SQL_MARKER.matcher(literal).find()) continue;

            // 선두 동사 전용 추출 — 쿼리 안의 문자열 값에 든 다른 키워드 오매칭 방지
            switch (am.group(1).toUpperCase()) {
                case "SELECT" -> {
                    Matcher m = RAW_SQL_SELECT.matcher(literal);
                    while (m.find()) result.add(new RawSqlAccess(m.group(1).toLowerCase(), false));
                }
                case "INSERT" -> {
                    Matcher m = RAW_SQL_INSERT.matcher(literal);
                    while (m.find()) result.add(new RawSqlAccess(m.group(1).toLowerCase(), true));
                }
                case "UPDATE" -> {
                    Matcher m = RAW_SQL_UPDATE.matcher(literal);
                    while (m.find()) result.add(new RawSqlAccess(m.group(1).toLowerCase(), true));
                }
                case "DELETE" -> {
                    Matcher m = RAW_SQL_DELETE.matcher(literal);
                    while (m.find()) result.add(new RawSqlAccess(m.group(1).toLowerCase(), true));
                }
            }
        }

        // 중복 제거 (같은 파일에서 동일 테이블 같은 타입 접근 반복 시)
        return result.stream().distinct().collect(java.util.stream.Collectors.toList());
    }

    // Django ORM 쓰기 매니저 메서드 — 그 외(get/filter/all/select_related 등)는 읽기로 분류
    private static final Set<String> DJANGO_WRITE_METHODS = Set.of(
        "create", "bulk_create", "get_or_create", "update_or_create",
        "update", "bulk_update", "delete"
    );
    // Django ORM 매니저 접근: EntityClass.objects.method(...) — 엔티티 클래스가 명시적이라 FP 낮음(generic find/save 회피).
    private static final Pattern DJANGO_OBJECTS_ACCESS =
        Pattern.compile("\\b([A-Z]\\w+)\\.objects\\.(\\w+)");
    // SQLAlchemy 읽기: Flask-SQLAlchemy `Entity.query`(지배적) — 대문자 수신자라 self/cls 자동 제외.
    private static final Pattern SQLALCHEMY_MODEL_QUERY =
        Pattern.compile("\\b([A-Z]\\w+)\\.query\\b");
    // SQLAlchemy 읽기: classic `session.query(Entity)` / `session.query(model.Entity)` — 인자 첫 대문자 토큰이 엔티티.
    private static final Pattern SQLALCHEMY_SESSION_QUERY =
        Pattern.compile("\\.query\\(\\s*(?:\\w+\\.)?([A-Z]\\w+)");
    // TypeORM 쓰기 메서드 — 그 외(find/findOne/count/findAndCount 등)는 읽기로 분류.
    private static final Set<String> TYPEORM_WRITE_METHODS = Set.of(
        "save", "insert", "update", "delete", "remove", "softDelete", "softRemove", "upsert",
        "increment", "decrement"
    );
    // TypeORM 리포지토리 필드 선언: `articleRepository: Repository<ArticleEntity>` → (필드명, 엔티티). 생성자 주입 프로퍼티 포함.
    private static final Pattern TYPEORM_REPO_FIELD =
        Pattern.compile("(\\w+)\\s*:\\s*Repository<(\\w+)>");
    // TypeORM 필드 경유 접근: `this.articleRepository.findOne(...)` → (필드명, 메서드).
    private static final Pattern TYPEORM_REPO_USE =
        Pattern.compile("this\\.(\\w+)\\.(\\w+)\\(");
    // TypeORM 직접 명시: `getRepository(ArticleEntity)` — 쿼리빌더 통상 읽기.
    private static final Pattern TYPEORM_GET_REPOSITORY =
        Pattern.compile("\\bgetRepository\\((\\w+)\\)");

    // 비JPA ORM 데이터 접근 추출 — 엔티티 클래스가 명시적으로 드러나는 패턴만(코드→DB_TABLE 엣지용, recall).
    // 미지의 클래스는 GraphBuilder가 entityClassToTableNodeId에 없으면 엣지를 안 만들어 자기제한적 precision.
    private List<DbAccess> extractDbAccesses(String content, String language) {
        if (language.equals("Python")) return extractPythonDbAccesses(content);
        if (language.equals("TypeScript") || language.equals("JavaScript")) return extractTypeOrmDbAccesses(content);
        return List.of();
    }

    // Python ORM 접근(Django objects + SQLAlchemy query) 추출
    private List<DbAccess> extractPythonDbAccesses(String content) {
        List<DbAccess> result = new ArrayList<>();
        Matcher m = DJANGO_OBJECTS_ACCESS.matcher(content);
        while (m.find()) {
            boolean isWrite = DJANGO_WRITE_METHODS.contains(m.group(2));
            result.add(new DbAccess(m.group(1), isWrite));
        }
        // SQLAlchemy 선언형 읽기(Entity.query / session.query(Entity)) — 전부 SELECT(READ).
        //   쓰기(session.add/delete(instance))는 인스턴스 변수라 엔티티 클래스가 호출부에 안 드러나 제외(precision, generic 회피).
        Matcher mq = SQLALCHEMY_MODEL_QUERY.matcher(content);
        while (mq.find()) result.add(new DbAccess(mq.group(1), false));
        Matcher sq = SQLALCHEMY_SESSION_QUERY.matcher(content);
        while (sq.find()) result.add(new DbAccess(sq.group(1), false));
        return result.stream().distinct().collect(java.util.stream.Collectors.toList());
    }

    // TypeORM 접근 추출 — 엔티티가 텍스트에 명시되는 패턴만(Repository<Entity> 필드 매핑·getRepository(Entity)).
    //   필드 경유 호출(this.repo.save/findOne)은 같은 파일 Repository<Entity> 선언으로 엔티티 해소 후 메서드로 r/w 분류.
    private List<DbAccess> extractTypeOrmDbAccesses(String content) {
        List<DbAccess> result = new ArrayList<>();
        // 필드명 → 엔티티 매핑 (Repository<Entity> 타입 선언)
        Map<String, String> repoFieldToEntity = new HashMap<>();
        Matcher rf = TYPEORM_REPO_FIELD.matcher(content);
        while (rf.find()) repoFieldToEntity.put(rf.group(1), rf.group(2));
        // this.field.method() — 매핑된 필드만, 메서드로 읽기/쓰기 구분
        Matcher use = TYPEORM_REPO_USE.matcher(content);
        while (use.find()) {
            String entity = repoFieldToEntity.get(use.group(1));
            if (entity == null) continue;
            result.add(new DbAccess(entity, TYPEORM_WRITE_METHODS.contains(use.group(2))));
        }
        // getRepository(Entity) — 직접 명시(쿼리빌더 통상 읽기)
        Matcher gr = TYPEORM_GET_REPOSITORY.matcher(content);
        while (gr.find()) result.add(new DbAccess(gr.group(1), false));
        return result.stream().distinct().collect(java.util.stream.Collectors.toList());
    }

    // .tsx/.jsx 파일에서 JSX 컴포넌트 사용 추출 — <ComponentName 패턴 (대문자 시작)
    private List<String> extractJsxComponents(String content, String relativePath) {
        if (!relativePath.endsWith(".tsx") && !relativePath.endsWith(".jsx")) return List.of();
        Pattern p = Pattern.compile("<([A-Z][\\w]*)(?:\\s|/?>)");
        Matcher m = p.matcher(content);
        Set<String> result = new LinkedHashSet<>();
        while (m.find()) result.add(m.group(1));
        // HTML 태그처럼 보이는 내장 컴포넌트 제외 (모두 대문자 시작이므로 이미 필터됨)
        return new ArrayList<>(result);
    }

    // 언어별 비동기 메서드/함수 목록 추출 (Java @Async / Python async def / TS async function)
    private List<String> extractAsyncMethods(String content, String language) {
        List<String> result = new ArrayList<>();

        if (language.equals("Java")) {
            // @Async 어노테이션(줄 시작) 다음 메서드명 탐지 — 주석/문자열 속 "@Async" 텍스트는 줄 시작이 아니므로 제외
            Matcher m = Pattern.compile(
                "^[ \\t]*@Async\\b[\\s\\S]{0,200}?(?:public|protected|private)\\s+(?:\\w+\\s+)*(\\w+)\\s*\\(",
                Pattern.MULTILINE
            ).matcher(content);
            while (m.find()) {
                String name = m.group(1);
                if (!isKeyword(name)) result.add(name);
            }

        } else if (language.equals("Python")) {
            // async def func_name(
            Matcher m = Pattern.compile("^\\s*async\\s+def\\s+(\\w+)\\s*\\(", Pattern.MULTILINE).matcher(content);
            while (m.find()) {
                String name = m.group(1);
                if (!isKeyword(name)) result.add(name);
            }

        } else if (language.equals("TypeScript") || language.equals("JavaScript")) {
            // async function name( / const name = async ( / async name(
            Matcher m = Pattern.compile(
                "(?:async\\s+function\\s+(\\w+)|(?:const|let|var)\\s+(\\w+)\\s*=\\s*async\\s*(?:\\(|\\w))",
                Pattern.MULTILINE
            ).matcher(content);
            while (m.find()) {
                String name = m.group(1) != null ? m.group(1) : m.group(2);
                if (name != null && !isKeyword(name)) result.add(name);
            }
        }

        return result;
    }

    // @Transactional 어노테이션이 붙은 메서드명 추출 (Spring 개념이라 Java/Kotlin만 해당)
    // JpaRepository 파생 쿼리 메서드는 인터페이스 추상 메서드라 접근제어자가 없는 경우가 흔해
    // (모디파이어를 요구하는 extractAsyncMethods 방식 대신) 스택 애노테이션까지 다루는
    // methodNameAfterAnnotation을 재사용 — extractFrameworkAnnotatedMethods와 동일 패턴
    private List<String> extractTransactionalMethods(String content, String language) {
        List<String> result = new ArrayList<>();
        if (!language.equals("Java") && !language.equals("Kotlin")) return result;

        String[] lines = content.split("\n");
        for (int i = 0; i < lines.length; i++) {
            if (!"Transactional".equals(leadingAnnotationName(lines[i]))) continue;
            String name = methodNameAfterAnnotation(lines, i, null);
            if (name != null) result.add(name);
        }
        return result;
    }

    // 파일의 최상위 타입이 interface로 선언되면(파일명=인터페이스명 관례, implementedInterfaces 매칭과 동일 전제)
    // 그 파일의 모든 메서드는 인터페이스 추상 메서드 — BROKEN_INTERFACE_CHAIN이 구현체 존재 여부를 판정하는 데 사용
    private List<String> extractInterfaceMethods(String content, String language, List<String> functions) {
        if (!language.equals("Java") && !language.equals("Kotlin")) return List.of();
        Matcher ifaceMatcher = Pattern.compile("\\binterface\\s+\\w[\\w<>]*[^{]*\\{").matcher(content);
        if (!ifaceMatcher.find()) return List.of();
        // Spring Data Repository(JpaRepository/CrudRepository/PagingAndSortingRepository) 파생 인터페이스는
        // 스프링이 런타임 프록시로 구현을 생성 — 소스에 implements/@Override 체인이 절대 나타나지 않는다
        // (도그푸딩 실측 2026-07-14: 이 가드 없이는 findByX/deleteByX 등 파생 쿼리 137건이 전부 오탐).
        if (Pattern.compile("extends\\s+(?:Jpa|Crud|PagingAndSorting)?Repository\\s*<").matcher(content).find()) {
            return List.of();
        }
        // 인터페이스 자신의 최상위 본문만 본다 — 중첩 class/record/interface/enum(예: 포트 인터페이스 안에
        // record 응답 타입을 선언하고 그 안에 헬퍼 메서드를 두는 패턴)의 메서드는 인터페이스 추상 메서드가
        // 아니므로 제외해야 한다(도그푸딩 실측: ProjectAccessPort 안 record의 isOwnRepo가 오탐으로 잡혔다).
        int bodyEnd = matchingBraceEnd(content, ifaceMatcher.end() - 1);
        if (bodyEnd < 0) return List.of();
        String ownBody = stripNestedTypeBodies(content.substring(ifaceMatcher.end(), bodyEnd));

        List<String> result = new ArrayList<>();
        for (String name : functions) {
            if (Pattern.compile("\\b" + Pattern.quote(name) + "\\s*\\(").matcher(ownBody).find()) {
                result.add(name);
            }
        }
        return result;
    }

    // openBraceIdx 위치의 '{'와 짝이 맞는 '}' 인덱스를 깊이 카운팅으로 찾는다 — 없으면 -1
    private int matchingBraceEnd(String content, int openBraceIdx) {
        int depth = 0;
        for (int i = openBraceIdx; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    // 본문 안의 중첩 class/record/interface/enum 선언 블록을 통째로 제거 — 최상위 멤버만 남긴다
    private String stripNestedTypeBodies(String body) {
        Matcher m = Pattern.compile("\\b(?:class|interface|record|enum)\\s+\\w[^{;]*\\{").matcher(body);
        List<int[]> spans = new ArrayList<>();
        while (m.find()) {
            int braceIdx = m.end() - 1;
            int end = matchingBraceEnd(body, braceIdx);
            if (end < 0) continue;
            spans.add(new int[]{m.start(), end + 1});
        }
        StringBuilder sb = new StringBuilder(body);
        for (int i = spans.size() - 1; i >= 0; i--) {
            sb.delete(spans.get(i)[0], spans.get(i)[1]);
        }
        return sb.toString();
    }

    // 프레임워크/런타임이 호출하는 메서드를 표시하는 어노테이션 — 부착 메서드는 코드상 호출부가 없어도 dead 아님
    private static final Set<String> JAVA_FRAMEWORK_ANNOTATIONS = Set.of(
        // Spring Web MVC 핸들러
        "GetMapping", "PostMapping", "PutMapping", "DeleteMapping", "PatchMapping", "RequestMapping",
        "InitBinder", "ModelAttribute", "ExceptionHandler",
        // Spring DI/라이프사이클 — 컨테이너가 호출
        "Bean", "EventListener", "Scheduled", "PostConstruct", "PreDestroy",
        // 인터페이스 구현/오버라이드 — 다형성 디스패치로 호출
        "Override",
        // 테스트 — 프레임워크가 리플렉션으로 호출
        "Test", "BeforeEach", "AfterEach", "BeforeAll", "AfterAll", "ParameterizedTest", "RepeatedTest"
    );

    // 어노테이션/데코레이터가 붙은 메서드명 추출 — 프레임워크가 호출하므로 FUNCTION_CALL 엣지가 없어도 dead 아님
    private List<String> extractFrameworkAnnotatedMethods(String content, String language) {
        List<String> result = new ArrayList<>();
        String[] lines = content.split("\n");

        if (language.equals("Java") || language.equals("Kotlin")) {
            for (int i = 0; i < lines.length; i++) {
                String ann = leadingAnnotationName(lines[i]);
                if (ann == null || !JAVA_FRAMEWORK_ANNOTATIONS.contains(ann)) continue;
                String name = methodNameAfterAnnotation(lines, i, null);
                if (name != null) result.add(name);
            }
        } else if (language.equals("Python")) {
            // 데코레이터(@app.route, @pytest.fixture, @property 등)가 붙은 def — 프레임워크 등록·런타임 호출
            for (int i = 0; i < lines.length; i++) {
                if (!lines[i].trim().startsWith("@")) continue;
                String name = methodNameAfterAnnotation(lines, i, "def");
                if (name != null) result.add(name);
            }
        } else if (language.equals("TypeScript") || language.equals("JavaScript")) {
            // 데코레이터(@Get, @Injectable 등 NestJS) 부착 메서드
            for (int i = 0; i < lines.length; i++) {
                if (!lines[i].trim().startsWith("@")) continue;
                String name = methodNameAfterAnnotation(lines, i, null);
                if (name != null) result.add(name);
            }
        }
        return result;
    }

    // 줄 앞쪽이 @로 시작하면 어노테이션 이름 반환 (없으면 null)
    private String leadingAnnotationName(String line) {
        String t = line.trim();
        if (!t.startsWith("@")) return null;
        int j = 1;
        while (j < t.length() && (Character.isLetterOrDigit(t.charAt(j)) || t.charAt(j) == '_')) j++;
        return j > 1 ? t.substring(1, j) : null;
    }

    // 어노테이션 줄(start)부터 앞쪽 어노테이션을 벗겨가며 메서드 시그니처 줄에서 메서드명 추출
    private String methodNameAfterAnnotation(String[] lines, int start, String defKeyword) {
        for (int k = start; k < Math.min(lines.length, start + 9); k++) {
            // 줄 앞쪽의 어노테이션/데코레이터(@Name·@a.b.c·@Name(...))를 제거
            String line = lines[k].replaceAll("^\\s*(?:@[\\w.]+(?:\\([^)]*\\))?\\s*)+", "").trim();
            if (line.isEmpty() || line.startsWith("//") || line.startsWith("*") || line.startsWith("/*")) continue;
            Matcher m = (defKeyword == null)
                    ? Pattern.compile("([A-Za-z_]\\w*)\\s*\\(").matcher(line)
                    : Pattern.compile(defKeyword + "\\s+(\\w+)\\s*\\(").matcher(line);
            if (m.find()) {
                String name = m.group(1);
                return isKeyword(name) ? null : name;
            }
            // 시그니처가 아닌 줄(필드 선언 등)을 만나면 중단
            return null;
        }
        return null;
    }
}
