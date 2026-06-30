// ContentHash SHA-256 검증 — 알려진 벡터·길이·내용 민감도
package com.codeprint.infrastructure.analysis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ContentHashTest {

    // "abc"의 SHA-256이 알려진 표준 벡터와 일치하는지
    @Test
    @DisplayName("알려진 SHA-256 벡터와 일치한다")
    void matchesKnownVector() {
        String hash = ContentHash.sha256("abc".getBytes(StandardCharsets.UTF_8));

        assertThat(hash).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    // 빈 입력도 64자 16진 해시를 반환하는지
    @Test
    @DisplayName("빈 입력은 64자 표준 해시를 반환한다")
    void emptyInputReturnsStandardHash() {
        String hash = ContentHash.sha256(new byte[0]);

        assertThat(hash).isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        assertThat(hash).hasSize(64);
    }

    // 1바이트만 달라도 해시가 달라지는지 (내용 변경 감지의 전제)
    @Test
    @DisplayName("내용이 1바이트만 달라도 해시가 달라진다")
    void differentContentDiffersHash() {
        String a = ContentHash.sha256("class Foo {}".getBytes(StandardCharsets.UTF_8));
        String b = ContentHash.sha256("class Foo { }".getBytes(StandardCharsets.UTF_8));

        assertThat(a).isNotEqualTo(b);
    }
}
