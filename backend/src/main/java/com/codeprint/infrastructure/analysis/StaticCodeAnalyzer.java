// 언어별 정규식으로 함수 정의와 import 구문을 추출하는 정적 분석기
package com.codeprint.infrastructure.analysis;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class StaticCodeAnalyzer {

    public ParsedFile analyze(Path file, Path repoRoot, String language) throws IOException {
        String content = Files.readString(file);
        String relativePath = repoRoot.relativize(file).toString().replace("\\", "/");

        List<String> functions = extractFunctions(content, language);
        List<String> imports = extractImports(content, language);

        return new ParsedFile(relativePath, language, functions, imports);
    }

    private List<String> extractFunctions(String content, String language) {
        Pattern pattern = switch (language) {
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

        if (pattern == null) return List.of();

        List<String> result = new ArrayList<>();
        Matcher m = pattern.matcher(content);
        while (m.find()) {
            for (int i = 1; i <= m.groupCount(); i++) {
                if (m.group(i) != null && !m.group(i).isBlank()) {
                    String name = m.group(i);
                    if (!isKeyword(name)) {
                        result.add(name);
                    }
                    break;
                }
            }
        }
        return result;
    }

    private List<String> extractImports(String content, String language) {
        Pattern pattern = switch (language) {
            case "Java" -> Pattern.compile("^import\\s+([\\w.]+);", Pattern.MULTILINE);
            case "Kotlin" -> Pattern.compile("^import\\s+([\\w.]+)", Pattern.MULTILINE);
            case "TypeScript", "JavaScript" ->
                Pattern.compile("(?:import|require)\\s*(?:\\{[^}]*\\}|[\\w*]+|\\(['\"])\\s*(?:from\\s*)?['\"]([^'\"]+)['\"]",
                        Pattern.MULTILINE);
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

    private boolean isKeyword(String name) {
        return java.util.Set.of(
                "if", "else", "for", "while", "switch", "try", "catch", "return",
                "new", "class", "interface", "enum", "void", "int", "long", "boolean",
                "String", "List", "Map", "Set", "var", "val", "let", "const"
        ).contains(name);
    }
}
