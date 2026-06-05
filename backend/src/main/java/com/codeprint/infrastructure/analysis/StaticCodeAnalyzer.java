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

        return new ParsedFile(relativePath, language, functions, imports, fileComment, functionComments, functionCalls, instantiatedClasses, dbTables, repositoryEntityClass);
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
        return result;
    }

    // 언어별 함수 정의 정규식 패턴 반환
    private Pattern getFunctionPattern(String language) {
        return switch (language) {
            case "Java", "Kotlin", "C#" ->
                Pattern.compile("(?:(?:public|private|protected|static|final|synchronized|abstract|default|native)\\s+)+(?:[\\w<>\\[\\]?,]+\\s+)*(\\w+)\\s*\\([^)]*\\)\\s*(?:throws[^{]+)?\\{",
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
            Matcher callMatcher = callPattern.matcher(body);
            Set<String> calls = new LinkedHashSet<>();
            while (callMatcher.find()) {
                String callee = callMatcher.group(1);
                if (!isKeyword(callee) && !callee.equals(funcName)) {
                    calls.add(callee);
                }
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

    // @Entity / Prisma model 블록에서 DB 테이블 정보 추출
    private List<DbTableInfo> extractDbTables(String content, String language, String filePath) {
        List<DbTableInfo> result = new ArrayList<>();

        // Java/Kotlin: @Entity 어노테이션이 있는 클래스
        if ((language.equals("Java") || language.equals("Kotlin")) && content.contains("@Entity")) {
            String tableName = null;
            // @Table(name = "table_name") 우선
            Matcher tableMatcher = Pattern.compile("@Table\\s*\\([^)]*name\\s*=\\s*[\"']([^\"']+)[\"']").matcher(content);
            if (tableMatcher.find()) tableName = tableMatcher.group(1);
            // 없으면 클래스명 사용
            if (tableName == null) {
                Matcher classMatcher = Pattern.compile("\\bclass\\s+(\\w+)").matcher(content);
                if (classMatcher.find()) tableName = classMatcher.group(1);
            }
            if (tableName != null) {
                String className = extractFileNameWithoutExt(filePath);
                result.add(new DbTableInfo(tableName, className));
            }
        }

        // Prisma schema.prisma: model 블록
        if (filePath.endsWith("schema.prisma")) {
            Matcher m = Pattern.compile("^model\\s+(\\w+)\\s*\\{", Pattern.MULTILINE).matcher(content);
            while (m.find()) {
                result.add(new DbTableInfo(m.group(1), m.group(1)));
            }
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

    // 파일 경로에서 확장자 제거 후 파일명만 추출
    private String extractFileNameWithoutExt(String filePath) {
        int slash = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
        String name = slash >= 0 ? filePath.substring(slash + 1) : filePath;
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    // 식별자가 언어 예약어인지 확인
    private boolean isKeyword(String name) {
        return Set.of("if", "else", "for", "while", "switch", "try", "catch", "return",
                "new", "class", "interface", "enum", "void", "int", "long", "boolean",
                "String", "List", "Map", "Set", "var", "val", "let", "const").contains(name);
    }
}
