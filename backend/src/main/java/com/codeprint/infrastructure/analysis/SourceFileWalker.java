// 클론된 레포에서 분석 대상 소스 파일 목록을 수집하는 유틸리티
package com.codeprint.infrastructure.analysis;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class SourceFileWalker {

    private static final Set<String> SKIP_DIRS = Set.of(
            ".git", "node_modules", "vendor", "target", "build", "dist",
            ".gradle", "__pycache__", ".idea", ".vscode"
    );

    private static final int MAX_FILES = 500;

    // 레포 루트에서 지원 언어 소스 파일을 최대 500개 수집 — 전체 대상 수를 함께 반환 (절단 감지)
    public WalkResult walk(Path repoRoot) throws IOException {
        List<Path> eligible = new ArrayList<>();
        Files.walkFileTree(repoRoot, new SimpleFileVisitor<>() {
            // 스킵 대상 디렉터리(node_modules·.git 등)는 하위 전체를 순회하지 않음 — 대형 디렉터리 순회 비용 제거 +
            // 그 안의 읽을 수 없는 엔트리로 walk 전체가 실패하는 것 방지 (기존 post-filter는 다 순회한 뒤 제외해 둘 다 못 막음)
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (!dir.equals(repoRoot) && SKIP_DIRS.contains(dir.getFileName().toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (attrs.isRegularFile()
                        && LanguageDetector.detect(file.getFileName().toString())
                            .map(LanguageDetector::isSupported).orElse(false)) {
                    eligible.add(file);
                }
                return FileVisitResult.CONTINUE;
            }

            // 읽을 수 없는 엔트리(권한·끊긴 심링크 등) 하나 때문에 레포 전체 분석이 실패하지 않도록 건너뛴다
            // — 제품 핵심이 "임의 GitHub 레포 분석"이므로 부분 수집이 전체 실패보다 낫다
            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.CONTINUE;
            }
        });
        List<Path> files = eligible.size() > MAX_FILES ? eligible.subList(0, MAX_FILES) : eligible;
        return new WalkResult(files, eligible.size());
    }
}
