// GitHub 레포지토리를 임시 디렉토리에 클론하는 유틸리티
package com.codeprint.infrastructure.analysis;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

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

    // GitHub 아카이브(tar.gz) 바이트를 임시 디렉토리에 안전하게 해제 — 특정 커밋 SHA 재분석용(git 프로토콜은
    // shallow clone에서 임의 SHA를 못 받아 codeload.github.com 아카이브로 우회). GitHub 아카이브는 항상
    // "{repo}-{sha}/..." 최상위 디렉터리로 감싸져 있어 첫 세그먼트를 제거해 git clone 결과와 동일한 트리로 맞춘다.
    public Path extractArchive(byte[] archiveBytes) throws IOException {
        Path tempDir = Files.createTempDirectory("codeprint-analysis-");
        try (TarArchiveInputStream tarIn = new TarArchiveInputStream(
                new GZIPInputStream(new ByteArrayInputStream(archiveBytes)))) {
            TarArchiveEntry entry;
            while ((entry = tarIn.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                int firstSlash = entry.getName().indexOf('/');
                if (firstSlash < 0) continue;
                String relativePath = entry.getName().substring(firstSlash + 1);
                if (relativePath.isBlank()) continue;

                // path traversal(zip-slip) 방지 — 해제 대상이 tempDir 밖으로 나가면 거부
                Path targetPath = tempDir.resolve(relativePath).normalize();
                if (!targetPath.startsWith(tempDir)) {
                    deleteDir(tempDir);
                    throw new IOException("아카이브에 안전하지 않은 경로가 있습니다: " + entry.getName());
                }
                Files.createDirectories(targetPath.getParent());
                Files.copy(tarIn, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
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
