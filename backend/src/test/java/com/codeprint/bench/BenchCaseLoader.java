// 클래스패스의 bench/ 리소스에서 expected.json을 가진 케이스 디렉터리를 탐색
package com.codeprint.bench;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public final class BenchCaseLoader {

    private BenchCaseLoader() {
    }

    // "bench/<subDir>" 아래에서 expected.json을 가진 케이스 디렉터리 전부(정렬됨) — 없으면 빈 목록
    public static List<Path> discoverCases(String subDir) {
        URL url = BenchCaseLoader.class.getClassLoader().getResource("bench/" + subDir);
        if (url == null) return List.of();
        try {
            Path root = Path.of(url.toURI());
            try (Stream<Path> walk = Files.walk(root)) {
                return walk.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().equals("expected.json"))
                        .map(Path::getParent)
                        .sorted()
                        .toList();
            }
        } catch (URISyntaxException | IOException e) {
            throw new IllegalStateException("벤치 케이스 탐색 실패: " + subDir, e);
        }
    }
}
