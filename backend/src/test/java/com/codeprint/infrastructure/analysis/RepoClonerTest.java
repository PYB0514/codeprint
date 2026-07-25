// RepoCloner.extractArchive 회귀 테스트 — 정상 해제·최상위 디렉터리 제거·path traversal 차단
package com.codeprint.infrastructure.analysis;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RepoClonerTest {

    private final RepoCloner repoCloner = new RepoCloner();

    // GitHub 아카이브 형식(최상위 "{repo}-{sha}/" 디렉터리로 감싼 tar.gz)을 흉내낸 테스트 픽스처 생성
    private byte[] buildTarGz(String... entryPathAndContent) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tarOut = new TarArchiveOutputStream(new GZIPOutputStream(baos))) {
            for (int i = 0; i < entryPathAndContent.length; i += 2) {
                String path = entryPathAndContent[i];
                byte[] content = entryPathAndContent[i + 1].getBytes(StandardCharsets.UTF_8);
                TarArchiveEntry entry = new TarArchiveEntry(path);
                entry.setSize(content.length);
                tarOut.putArchiveEntry(entry);
                tarOut.write(content);
                tarOut.closeArchiveEntry();
            }
            tarOut.finish();
        }
        return baos.toByteArray();
    }

    @Test
    @DisplayName("최상위 {repo}-{sha}/ 디렉터리를 제거하고 하위 파일을 그대로 해제한다")
    void extractArchive_최상위디렉터리_제거하고_정상해제() throws IOException {
        byte[] archive = buildTarGz(
                "codeprint-abc123/README.md", "hello",
                "codeprint-abc123/src/Main.java", "class Main {}"
        );

        Path result = repoCloner.extractArchive(archive);

        assertThat(Files.readString(result.resolve("README.md"))).isEqualTo("hello");
        assertThat(Files.readString(result.resolve("src/Main.java"))).isEqualTo("class Main {}");
        // 최상위 아카이브 디렉터리명 자체는 결과 트리에 남지 않아야 함
        assertThat(Files.exists(result.resolve("codeprint-abc123"))).isFalse();

        repoCloner.deleteDir(result);
    }

    @Test
    @DisplayName("아카이브 엔트리 경로에 ../ 가 있어 해제 대상 밖으로 나가면 거부한다(path traversal 방지)")
    void extractArchive_경로이탈_엔트리는_거부() throws IOException {
        byte[] archive = buildTarGz(
                "codeprint-abc123/../../evil.txt", "malicious"
        );

        assertThatThrownBy(() -> repoCloner.extractArchive(archive))
                .isInstanceOf(IOException.class);
    }
}
