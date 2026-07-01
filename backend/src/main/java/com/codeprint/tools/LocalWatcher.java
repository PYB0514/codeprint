// 로컬 디렉터리를 감시하다 파일이 바뀌면 증분 재분석 후 워닝 변화(diff)만 출력하는 CLI 데몬
package com.codeprint.tools;

import com.codeprint.infrastructure.analysis.CachedParsedFileLoader;
import com.codeprint.infrastructure.analysis.StaticCodeAnalyzer;

import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class LocalWatcher {

    private static final Set<String> SKIP_DIRS = Set.of(
            ".git", "node_modules", "vendor", "target", "build", "dist",
            ".gradle", "__pycache__", ".idea", ".vscode"
    );

    private static final Duration DEBOUNCE = Duration.ofMillis(500);

    public static void main(String[] args) throws Exception {
        Path rootDir = args.length > 0 ? Path.of(args[0]) : Path.of(".");
        System.out.println("워치 대상: " + rootDir.toAbsolutePath() + " (종료: Ctrl+C)");

        UUID projectId = UUID.randomUUID();
        CachedParsedFileLoader loader = new CachedParsedFileLoader(new StaticCodeAnalyzer(), new InMemoryParsedFileCachePort());

        WatchService watchService = rootDir.getFileSystem().newWatchService();
        registerRecursive(rootDir, watchService);

        Set<String> previousWarnings = warningKeys(LocalAnalyzer.analyze(rootDir, projectId, loader));
        printSummary(previousWarnings.size());

        while (true) {
            WatchKey key = watchService.take();
            key.pollEvents();
            drainDebounce(watchService);
            key.reset();

            Set<String> current = warningKeys(LocalAnalyzer.analyze(rootDir, projectId, loader));
            printDiff(previousWarnings, current);
            previousWarnings = current;
        }
    }

    // 이벤트가 연달아 들어오는 저장 순간을 하나로 묶는다 — DEBOUNCE 동안 새 이벤트가 없을 때까지 대기
    // pollEvents()로 큐를 비우지 않고 reset()만 하면 키가 즉시 재신호되어 무한 재분석 루프가 됨(실제 관찰된 버그)
    private static void drainDebounce(WatchService watchService) throws InterruptedException {
        WatchKey next;
        while ((next = watchService.poll(DEBOUNCE.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)) != null) {
            next.pollEvents();
            next.reset();
        }
    }

    // 루트 이하 전체 서브디렉터리를 WatchService에 등록 (SKIP_DIRS는 건너뜀)
    private static void registerRecursive(Path rootDir, WatchService watchService) throws Exception {
        Files.walkFileTree(rootDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws java.io.IOException {
                if (!dir.equals(rootDir) && SKIP_DIRS.contains(dir.getFileName().toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                dir.register(watchService,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_DELETE);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static Set<String> warningKeys(List<Map<String, Object>> warnings) {
        Set<String> keys = new HashSet<>();
        for (Map<String, Object> w : warnings) {
            keys.add(w.get("type") + "|" + w.get("message"));
        }
        return keys;
    }

    private static void printSummary(int count) {
        System.out.println("초기 워닝 " + count + "개. 파일 변경을 기다리는 중...\n");
    }

    // 직전 실행과 비교해 새로 생긴/해소된 워닝만 출력 — 변화 없으면 아무것도 출력하지 않는다
    // (OS가 저장 한 번을 여러 이벤트로 쪼개 전달할 때 디바운스를 통과해 재분석이 반복될 수 있어, 조용한 결과는 표시하지 않음)
    private static void printDiff(Set<String> previous, Set<String> current) {
        Set<String> added = new HashSet<>(current);
        added.removeAll(previous);
        Set<String> resolved = new HashSet<>(previous);
        resolved.removeAll(current);

        if (added.isEmpty() && resolved.isEmpty()) return;

        for (String key : added) System.out.println("  🆕 " + key);
        for (String key : resolved) System.out.println("  ✅ 해소: " + key);
        System.out.println("현재 워닝 " + current.size() + "개\n");
    }
}
