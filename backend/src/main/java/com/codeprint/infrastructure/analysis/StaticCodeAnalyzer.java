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

    // 단일 소스 파일을 분석하여 함수명, import, 주석 등을 추출
    public ParsedFile analyze(Path file, Path repoRoot, String language) throws IOException {
        String content = Files.readString(file, StandardCharsets.UTF_8);
        String relativePath = repoRoot.relativize(file).toString().replace("\\", "/");

        List<String> functions = extractFunctions(content, language);
        List<String> imports = extractImports(content, language);
        String fileComment = extractFileComment(content, language);
        Map<String, String> functionComments = extractFunctionComments(content, language);
        Map<String, List<String>> functionCalls = extractFunctionCalls(content, language, functions);
        List<String> instantiatedClasses = extractInstantiatedClasses(content);
        List<DbTableInfo> dbTables = extractDbTables(content, language, relativePath);
        String repositoryEntityClass = extractRepositoryEntityClass(content, language);
        List<ColumnInfo> entityColumns = extractEntityColumns(content, language);
        List<String> apiCalls = extractApiCalls(content, language);
        List<String> controllerMappings = extractControllerMappings(content, language);
        List<String> implementedInterfaces = extractImplementedInterfaces(content, language);
        List<String> asyncMethods = extractAsyncMethods(content, language);
        List<String> jsxComponents = extractJsxComponents(content, relativePath);

        return new ParsedFile(relativePath, language, functions, imports, fileComment, functionComments, functionCalls, instantiatedClasses, dbTables, repositoryEntityClass, entityColumns, apiCalls, controllerMappings, implementedInterfaces, asyncMethods, jsxComponents);
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
            case "Go" ->
                Pattern.compile("^func\\s+(?:\\(\\w+\\s+\\*?\\w+\\)\\s+)?(\\w+)\\s*\\(", Pattern.MULTILINE);
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
        Pattern pattern = switch (language) {
            case "Java" -> Pattern.compile("^import\\s+([\\w.]+);", Pattern.MULTILINE);
            case "Kotlin" -> Pattern.compile("^import\\s+([\\w.]+)", Pattern.MULTILINE);
            case "TypeScript", "JavaScript" ->
                Pattern.compile("from\\s+['\"]([^'\"]+)['\"]", Pattern.MULTILINE);
            case "Python" ->
                Pattern.compile("^(?:from\\s+([\\w.]+)\\s+import|import\\s+([\\w.,\\s]+))", Pattern.MULTILINE);
            case "Go" -> Pattern.compile("\"([\\w./]+)\"", Pattern.MULTILINE);
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

    // 각 함수 본문에서 호출하는 함수명 목록 추출 — 함수명(파라미터) 패턴 스캔
    private Map<String, List<String>> extractFunctionCalls(String content, String language, List<String> definedFunctions) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        if (definedFunctions.isEmpty()) return result;

        // 함수 정의 경계를 찾기 위한 패턴
        Pattern funcDefPattern = getFunctionPattern(language);
        if (funcDefPattern == null) return result;

        // 함수 호출 패턴: 식별자 뒤에 '(' — 키워드·생성자(대문자 시작) 제외
        Pattern callPattern = Pattern.compile("\\b([a-z][a-zA-Z0-9_]*)\\s*\\(");
        // 정적 팩토리/클래스 메서드 호출: ClassName.method() — 클래스명 대문자 시작
        Pattern qualifiedCallPattern = Pattern.compile("\\b([A-Z][a-zA-Z0-9_]*)\\.([a-z][a-zA-Z0-9_]*)\\s*\\(");

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

    // TypeScript/JS 파일에서 axios HTTP 호출 경로 추출 ("METHOD:/path" 형식)
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

        } else if (language.equals("Python")) {
            // FastAPI/Flask: @app.get('/path') / @router.post('/path')
            Matcher m = Pattern.compile(
                "@(?:app|router|bp|blueprint)\\.(get|post|put|delete|patch)\\s*\\(\\s*['\"`]([^'\"`\\n]+)['\"`]",
                Pattern.CASE_INSENSITIVE
            ).matcher(content);
            while (m.find()) {
                result.add(m.group(1).toUpperCase() + ":" + m.group(2));
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
            // @Async 바로 다음(0~3줄 이내)에 오는 메서드명 탐지
            Matcher m = Pattern.compile(
                "@Async[\\s\\S]{0,200}?(?:public|protected|private)\\s+(?:\\w+\\s+)*(\\w+)\\s*\\(",
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
}
