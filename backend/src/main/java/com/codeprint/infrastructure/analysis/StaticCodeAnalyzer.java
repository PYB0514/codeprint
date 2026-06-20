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

    // 단일 소스 파일을 분석하여 함수명, import, 주석 등을 추출
    public ParsedFile analyze(Path file, Path repoRoot, String language) throws IOException {
        String content = Files.readString(file, StandardCharsets.UTF_8);
        // UTF-8 BOM 제거 — tree-sitter 바이트 오프셋은 BOM을 제외하는데 content.getBytes()는 BOM(3바이트)을 포함해
        // 오프셋이 어긋나면 모든 식별자 추출이 밀린다(.NET 소스는 BOM 저장이 흔함). 정규식 경로에도 무해(보이지 않는 선두 문자 제거).
        if (!content.isEmpty() && content.charAt(0) == '\uFEFF') content = content.substring(1);
        String relativePath = repoRoot.relativize(file).toString().replace("\\", "/");

        // 식별자 검출기용 — 주석 본문을 공백으로 치환한 길이 보존 사본 (B-10 Stage 1).
        // 주석/문자열 페이로드를 읽는 검출기(주석 라벨·API 경로·raw SQL 등)는 원본 content를 그대로 쓴다.
        String masked = maskComments(content, language);

        // Java·Python·TypeScript/JavaScript·Go·Rust·C#·Ruby·PHP 함수·호출은 tree-sitter(AST)로 추출 — 오탐 제거·정확한 호출 귀속(중첩/메서드).
        // tree-sitter는 raw content를 직접 파싱(AST가 주석·문자열을 구분하므로 masking 불필요). 실패 시 정규식 폴백.
        List<String> functions;
        Map<String, List<String>> functionCalls;
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
        if (javaTs.isPresent()) {
            functions = javaTs.get().functions();
            functionCalls = javaTs.get().functionCalls();
        } else if (pyTs.isPresent()) {
            functions = pyTs.get().functions();
            functionCalls = pyTs.get().functionCalls();
        } else if (tsTs.isPresent()) {
            functions = tsTs.get().functions();
            functionCalls = tsTs.get().functionCalls();
        } else if (goTs.isPresent()) {
            functions = goTs.get().functions();
            functionCalls = goTs.get().functionCalls();
        } else if (rustTs.isPresent()) {
            functions = rustTs.get().functions();
            functionCalls = rustTs.get().functionCalls();
        } else if (csTs.isPresent()) {
            functions = csTs.get().functions();
            functionCalls = csTs.get().functionCalls();
        } else if (rubyTs.isPresent()) {
            functions = rubyTs.get().functions();
            functionCalls = rubyTs.get().functionCalls();
        } else if (phpTs.isPresent()) {
            functions = phpTs.get().functions();
            functionCalls = phpTs.get().functionCalls();
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
        List<String> implementedInterfaces = extractImplementedInterfaces(masked, language);
        List<String> asyncMethods = extractAsyncMethods(masked, language);
        List<String> jsxComponents = extractJsxComponents(masked, relativePath);
        List<RawSqlAccess> rawSqlAccesses = extractRawSqlAccesses(content);
        List<String> frameworkAnnotatedMethods = extractFrameworkAnnotatedMethods(masked, language);

        return new ParsedFile(relativePath, language, functions, imports, fileComment, functionComments, functionCalls, instantiatedClasses, dbTables, repositoryEntityClass, entityColumns, apiCalls, controllerMappings, implementedInterfaces, asyncMethods, jsxComponents, rawSqlAccesses, frameworkAnnotatedMethods, valueReferencedFunctions, functionDefCounts);
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
            case "TypeScript", "JavaScript" ->
                Pattern.compile("(?:function\\s+(\\w+)|(?:const|let|var)\\s+(\\w+)\\s*=\\s*(?:async\\s*)?(?:\\([^)]*\\)|\\w+)\\s*=>|(?:async\\s+)?function\\s*\\*?\\s*(\\w+))",
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
        if ((language.equals("TypeScript") || language.equals("JavaScript")) && content.contains("@Entity")) {
            // @Entity('table_name') 우선, 없으면 클래스명
            String tableName = null;
            Matcher teMatcher = Pattern.compile("@Entity\\s*\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)").matcher(content);
            if (teMatcher.find()) tableName = teMatcher.group(1);
            if (tableName == null) {
                Matcher classMatcher = Pattern.compile("\\bclass\\s+(\\w+)").matcher(content);
                if (classMatcher.find()) tableName = classMatcher.group(1);
            }
            if (tableName != null) {
                result.add(new DbTableInfo(tableName, extractFileNameWithoutExt(filePath)));
            }
        }

        // Python: SQLAlchemy Base 상속 클래스 (class X(Base):)
        if (language.equals("Python")) {
            Matcher m = Pattern.compile("^class\\s+(\\w+)\\s*\\([^)]*Base[^)]*\\)\\s*:", Pattern.MULTILINE).matcher(content);
            while (m.find()) {
                String className = m.group(1);
                // __tablename__ 우선 추출
                Matcher tnMatcher = Pattern.compile("__tablename__\\s*=\\s*['\"]([^'\"]+)['\"]").matcher(content);
                String tableName = tnMatcher.find() ? tnMatcher.group(1) : className;
                result.add(new DbTableInfo(tableName, className));
            }
        }

        // Python: Django ORM (class X(models.Model):) — 추상 모델 제외, Meta.db_table 우선
        if (language.equals("Python")) {
            Matcher m = Pattern.compile("^class\\s+(\\w+)\\s*\\([^)]*\\bmodels\\.Model\\b[^)]*\\)\\s*:", Pattern.MULTILINE).matcher(content);
            while (m.find()) {
                String className = m.group(1);
                // 모델 본문(다음 최상위 class 전까지)에서 추상 여부·db_table 판정
                String block = content.substring(m.end(), nextTopLevelClassIndex(content, m.end()));
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
        if (!m.find()) return List.of();
        List<String> result = new ArrayList<>();
        for (String part : m.group(1).split(",")) {
            String name = part.trim().replaceAll("<[^>]*>", "").trim();
            if (!name.isEmpty()) result.add(name);
        }
        return result;
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
