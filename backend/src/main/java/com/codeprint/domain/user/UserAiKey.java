// BYOK(사용자 소유 LLM API 키) 도메인 엔티티
package com.codeprint.domain.user;

import com.codeprint.shared.ai.AiProvider;
import com.codeprint.shared.jpa.AesEncryptionConverter;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_ai_keys")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserAiKey {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    private AiProvider provider;

    @Convert(converter = AesEncryptionConverter.class)
    @Column(name = "api_key_encrypted", nullable = false, columnDefinition = "text")
    private String apiKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // 신규 키 등록
    public static UserAiKey create(UUID userId, AiProvider provider, String plainApiKey) {
        UserAiKey key = new UserAiKey();
        key.id = UUID.randomUUID();
        key.userId = userId;
        key.provider = provider;
        key.apiKey = plainApiKey;
        Instant now = Instant.now();
        key.createdAt = now;
        key.updatedAt = now;
        return key;
    }

    // 키 회전(재등록)
    public void rotate(String newPlainApiKey) {
        this.apiKey = newPlainApiKey;
        this.updatedAt = Instant.now();
    }
}
