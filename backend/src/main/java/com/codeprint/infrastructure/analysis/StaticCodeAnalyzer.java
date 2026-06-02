// 언어별 정규식으로 함수 정의, import, 주석을 추출하는 정적 분석기
package com.codeprint.infrastructure.analysis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(StaticCodeAnalyzer.class);

    // 단일 소스 파일을 분석하여 함수명, import, 주석 등을 추출
    public ParsedFile analyze(Path file, Path repoRoot, String language) throws IOException {
        String content = Files.readString(file, StandardCharsets.UTF_8);
        String relativePath = repoRoot.relativize(file).toString().replace("\\", "/");

        List<String> functions = extractFunctions(content, language);
        List<String> imports = extractImports(content, language);
        String fileComment = extractFileComment(content, language);
        Map<String, String> functionComments = extractFunctionComments(content, language);

        if (fileComment != null || !functionComments.isEmpty()) {
            log.debug("[주석추출] {} | 파일주석={} | 함수주석={}개", relativePath, fileComment, functionComments.size());
        }

        return new ParsedFile(relativePath, language, functions, imports, fileComment, functionComments);
    }

    // 파일 상단 첫 번째 주석 추출
    private String extractFileComment(String content, String language) {
        String[] lines = content.split("\n");
        boolean logged = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            if (!logged) {
                logged = true;
                String preview = trimmed.length() > 60 ? trimmed.substring(0, 60) : trimmed;
                log.debug("[extractFileComment] lang={} | 첫char=U+{} | preview={}",
                        language,
                        String.format("%04X", (int) trimmed.charAt(0)),
                        preview);
            }
            if (language.equals("Python")) {
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

    // 함수 바로 위 한 줄 주석 추출 (함수명 → 주석 맵)
    private Map<String, String> extractFunctionComments(String content, String language) {
        Map<String, String> result = new LinkedHashMap<>();
        String[] lines = content.split("\n");

        Pattern funcPattern = getFunctionPattern(language);
        if (funcPattern == null) return result;

        for (int i = 0; i < lines.length; i++) {
            Matcher m = funcPattern.matcher(lines[i]);
            if (m.find()) {
                String funcName = extractFirstGroup(m);
                if (funcName == null || isKeyword(funcName)) continue;

                // 바로 위 줄에서 주석 찾기 (어노테이션은 건너뜀)
                String comment = null;
                for (int j = i - 1; j >= Math.max(0, i - 8); j--) {
                    String prev = lines[j].trim();
                    if (prev.isEmpty()) continue;
                    if (prev.startsWith("@")) continue; // 어노테이션 건너뜀
                    if (prev.startsWith("//")) {
                        comment = prev.substring(2).trim();
                    } else if (prev.startsWith("*") && !prev.startsWith("*/")) {
                        comment = prev.replaceAll("^\\*+\\s*", "").trim();
                    } else if (prev.startsWith("#") && language.equals("Python")) {
                        comment = prev.substring(1).trim();
                    }
                    break;
                }
                if (comment != null && !comment.isBlank()) {
                    result.put(funcName, comment);
                }
            }
        }
        return result;
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
                Pattern.compile("(?:public|private|protected|internal|static|\\s)+(?:\\w+\\s+)?(\\w+)\\s*\\([^)]*\\)\\s*(?:throws[^{]+)?\\{",
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

    // 식별자가 언어 예약어인지 확인
    private boolean isKeyword(String name) {
        return Set.of("if", "else", "for", "while", "switch", "try", "catch", "return",
                "new", "class", "interface", "enum", "void", "int", "long", "boolean",
                "String", "List", "Map", "Set", "var", "val", "let", "const").contains(name);
    }
}
