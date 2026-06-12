// 사용자별 AI 제공자 API 키 도메인 엔티티
package com.codeprint.domain.ai;

import com.codeprint.shared.jpa.AesEncryptionConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_ai_keys")
@Getter
@NoArgsConstructor
public class UserAiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AiProvider provider;

    @Convert(converter = AesEncryptionConverter.class)
    @Column(name = "api_key_encrypted", nullable = false)
    private String apiKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // API 키 신규 저장
    public static UserAiKey of(UUID userId, AiProvider provider, String apiKey) {
        UserAiKey key = new UserAiKey();
        key.userId = userId;
        key.provider = provider;
        key.apiKey = apiKey;
        key.createdAt = Instant.now();
        key.updatedAt = Instant.now();
        return key;
    }

    // API 키 갱신
    public void updateApiKey(String newApiKey) {
        this.apiKey = newApiKey;
        this.updatedAt = Instant.now();
    }
}
