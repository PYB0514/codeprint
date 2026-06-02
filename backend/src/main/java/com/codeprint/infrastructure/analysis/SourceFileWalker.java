// 클론된 레포에서 분석 대상 소스 파일 목록을 수집하는 유틸리티
package com.codeprint.infrastructure.analysis;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

@Component
public class SourceFileWalker {

    private static final Set<String> SKIP_DIRS = Set.of(
            ".git", "node_modules", "vendor", "target", "build", "dist",
            ".gradle", "__pycache__", ".idea", ".vscode"
    );

    private static final int MAX_FILES = 500;

    public List<Path> walk(Path repoRoot) throws IOException {
        try (Stream<Path> stream = Files.walk(repoRoot)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> !isInSkipDir(repoRoot, p))
                    .filter(p -> LanguageDetector.detect(p.getFileName().toString())
                            .map(LanguageDetector::isSupported)
                            .orElse(false))
                    .limit(MAX_FILES)
                    .toList();
        }
    }

    private boolean isInSkipDir(Path root, Path file) {
        Path relative = root.relativize(file);
        for (Path part : relative) {
            if (SKIP_DIRS.contains(part.toString())) return true;
        }
        return false;
    }
}
