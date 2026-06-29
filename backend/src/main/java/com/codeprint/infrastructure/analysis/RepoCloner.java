// GitHub 레포지토리를 임시 디렉토리에 클론하는 유틸리티
package com.codeprint.infrastructure.analysis;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Component
public class RepoCloner {

    // clone이 이 시간을 넘기면 강제 종료 — 행(hang) 방지
    private static final long CLONE_TIMEOUT_SECONDS = 120;

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
        // 자격증명 프롬프트를 끔 — 비공개 레포에 토큰이 없으면 git이 입력 대기로 영구 행하던 것 방지
        pb.environment().put("GIT_TERMINAL_PROMPT", "0");
        Process process = pb.start();

        boolean finished = process.waitFor(CLONE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            deleteDir(tempDir);
            throw new IOException("git clone timed out after " + CLONE_TIMEOUT_SECONDS + "s");
        }
        if (process.exitValue() != 0) {
            String output = new String(process.getInputStream().readAllBytes());
            deleteDir(tempDir);
            throw new IOException("git clone failed (exit=" + process.exitValue() + "): " + output);
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
