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

    // 레포 루트에서 지원 언어 소스 파일을 최대 500개 수집 — 전체 대상 수를 함께 반환 (절단 감지)
    public WalkResult walk(Path repoRoot) throws IOException {
        try (Stream<Path> stream = Files.walk(repoRoot)) {
            List<Path> eligible = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> !isInSkipDir(repoRoot, p))
                    .filter(p -> LanguageDetector.detect(p.getFileName().toString())
                            .map(LanguageDetector::isSupported)
                            .orElse(false))
                    .toList();
            List<Path> files = eligible.size() > MAX_FILES ? eligible.subList(0, MAX_FILES) : eligible;
            return new WalkResult(files, eligible.size());
        }
    }

    // 파일이 스킵 대상 디렉토리(node_modules, .git 등) 안에 있는지 확인
    private boolean isInSkipDir(Path root, Path file) {
        Path relative = root.relativize(file);
        for (Path part : relative) {
            if (SKIP_DIRS.contains(part.toString())) return true;
        }
        return false;
    }
}
