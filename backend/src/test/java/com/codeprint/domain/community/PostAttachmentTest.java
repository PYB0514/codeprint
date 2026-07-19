// PostAttachment 엔티티 단위 테스트 — s3Key 프리픽스 검증(버킷 내 타 리소스 참조 차단) 회귀 방지
package com.codeprint.domain.community;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PostAttachmentTest {

    private final UUID postId = UUID.randomUUID();

    @Test
    @DisplayName("create — attachments/ 프리픽스+UUID 형식의 s3Key는 정상 생성")
    void create_validKey_succeeds() {
        String key = "attachments/" + UUID.randomUUID() + "/report.pdf";

        PostAttachment attachment = PostAttachment.create(postId, key, "report.pdf", "application/pdf");

        assertThat(attachment.getS3Key()).isEqualTo(key);
    }

    @Test
    @DisplayName("create — db-backups 등 타 프리픽스를 가리키는 s3Key는 거부")
    void create_otherPrefixKey_throws() {
        assertThatThrownBy(() ->
                PostAttachment.create(postId, "db-backups/codeprint-20260718T030000Z.sql.gz", "x", "text/plain"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("create — attachments/ 프리픽스라도 UUID 형식이 아니면 거부")
    void create_malformedUuidSegment_throws() {
        assertThatThrownBy(() ->
                PostAttachment.create(postId, "attachments/not-a-uuid/file.txt", "x", "text/plain"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("create — s3Key가 null이면 거부")
    void create_nullKey_throws() {
        assertThatThrownBy(() ->
                PostAttachment.create(postId, null, "x", "text/plain"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
