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
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Component
public class SourceFileWalker {

    private static final Set<String> SKIP_DIRS = Set.of(
            ".git", "node_modules", "vendor", "target", "build", "dist",
            ".gradle", "__pycache__", ".idea", ".vscode", "docsets"
    );

    private static final int MAX_FILES = 500;

    // 레포 루트에서 지원 언어 소스 파일을 최대 500개 수집 — 전체 대상 수를 함께 반환 (절단 감지)
    // 절단 시 사전순 뒤쪽 서브트리가 통째로 사라지는 편향을 줄이기 위해 프로덕션 소스를 먼저 채우고
    // 테스트·픽스처는 남는 슬롯만 사용(GATE_GAPS.md [G-9] T2 후순위화)
    public WalkResult walk(Path repoRoot) throws IOException {
        List<Path> eligible = new ArrayList<>();
        Files.walkFileTree(repoRoot, new SimpleFileVisitor<>() {
            // 스킵 대상 디렉터리(node_modules·.git 등)는 하위 전체를 순회하지 않음 — 대형 디렉터리 순회 비용 제거 +
            // 그 안의 읽을 수 없는 엔트리로 walk 전체가 실패하는 것 방지 (기존 post-filter는 다 순회한 뒤 제외해 둘 다 못 막음)
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                String dirName = dir.getFileName() != null ? dir.getFileName().toString() : "";
                if (!dir.equals(repoRoot) && (SKIP_DIRS.contains(dirName) || dirName.endsWith(".docset"))) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (attrs.isRegularFile()
                        && !isMinified(file.getFileName().toString())
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
        // 절단 여부와 무관하게 항상 정렬 — 파일시스템 순회 순서(플랫폼·OS마다 다름, 정렬 보장 없음)에
        // 결과가 좌우되지 않도록 경로 문자열 기준 결정론을 보장한다(같은 커밋을 두 번 분석해도 같은 500개가 선택됨)
        eligible.sort(Comparator.comparing(Path::toString));
        List<Path> production = new ArrayList<>();
        List<Path> testOrFixture = new ArrayList<>();
        for (Path p : eligible) {
            (isTestOrFixturePath(repoRoot, p) ? testOrFixture : production).add(p);
        }
        List<Path> tiered = new ArrayList<>(production);
        tiered.addAll(testOrFixture);
        List<Path> files = tiered.size() > MAX_FILES ? tiered.subList(0, MAX_FILES) : tiered;
        return new WalkResult(files, eligible.size());
    }

    // 미니파이 번들 제외 — mangled 식별자라 판정·시각화 모두 무의미하고 슬롯·저장·phantom 정확도만 갉아먹는다(jquery.min.js 등)
    private static boolean isMinified(String fileName) {
        return fileName.contains(".min.");
    }

    // 테스트·픽스처 경로 여부 — GraphWarningService.isTestPath와 같은 기준(레이어 분리로 여기선 별도 구현, application 계층 역참조 방지)
    private static boolean isTestOrFixturePath(Path repoRoot, Path file) {
        String rel = repoRoot.relativize(file).toString().replace('\\', '/');
        return rel.contains("src/test/") || rel.contains("__tests__/")
                || rel.contains(".test.") || rel.contains(".spec.");
    }
}
