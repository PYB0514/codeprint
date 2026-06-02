// GitHub 레포지토리를 임시 디렉토리에 클론하는 유틸리티
package com.codeprint.infrastructure.analysis;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

@Component
public class RepoCloner {

    // GitHub 레포를 지정 브랜치로 shallow clone (branch null이면 기본 브랜치)
    public Path clone(String githubRepoUrl, String branch) throws IOException, InterruptedException {
        Path tempDir = Files.createTempDirectory("codeprint-analysis-");

        java.util.List<String> cmd = new java.util.ArrayList<>(java.util.Arrays.asList(
                "git", "clone", "--depth=1", "--quiet"
        ));
        if (branch != null && !branch.isBlank()) {
            cmd.addAll(java.util.Arrays.asList("--branch", branch));
        }
        cmd.add(githubRepoUrl);
        cmd.add(tempDir.toString());

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            String output = new String(process.getInputStream().readAllBytes());
            deleteDir(tempDir);
            throw new IOException("git clone failed (exit=" + exitCode + "): " + output);
        }

        return tempDir;
    }

    // 임시 디렉토리와 하위 파일 전체 삭제
    public void deleteDir(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                .forEach(p -> p.toFile().delete());
        } catch (IOException ignored) {}
    }
}
