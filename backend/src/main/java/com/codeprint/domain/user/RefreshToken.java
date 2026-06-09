// Refresh Token 도메인 엔티티
package com.codeprint.domain.user;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    // SHA-256 해시 저장 — 원본 토큰은 DB에 남기지 않음
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // 새 Refresh Token 생성
    public static RefreshToken create(UUID userId, String tokenHash, Instant expiresAt) {
        RefreshToken rt = new RefreshToken();
        rt.id = UUID.randomUUID();
        rt.userId = userId;
        rt.tokenHash = tokenHash;
        rt.expiresAt = expiresAt;
        rt.createdAt = Instant.now();
        return rt;
    }

    // 만료 여부 확인
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
