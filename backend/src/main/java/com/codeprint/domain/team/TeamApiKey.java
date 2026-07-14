// 팀 단위로 발급되는 API 키 — 비공개 프로젝트 교차 조회 인증용, 평문은 저장하지 않고 해시만 보관
package com.codeprint.domain.team;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Entity
@Table(name = "team_api_keys")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TeamApiKey {

    private static final String PREFIX = "cpk_";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "team_id", nullable = false, columnDefinition = "uuid")
    private UUID teamId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "key_hash", nullable = false, length = 64)
    private String keyHash;

    @Column(name = "key_prefix", nullable = false, length = 12)
    private String keyPrefix;

    @Column(name = "created_by", nullable = false, columnDefinition = "uuid")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    // 신규 키 발급 — 평문은 이 호출의 반환값에만 담기고 엔티티엔 해시만 저장된다(재조회 불가)
    public static IssuedKey issue(UUID teamId, String name, UUID createdBy) {
        String raw = PREFIX + randomToken();
        TeamApiKey key = new TeamApiKey();
        key.id = UUID.randomUUID();
        key.teamId = teamId;
        key.name = name;
        key.keyHash = hash(raw);
        key.keyPrefix = raw.substring(0, PREFIX.length() + 8);
        key.createdBy = createdBy;
        key.createdAt = Instant.now();
        return new IssuedKey(key, raw);
    }

    // 평문 키가 이 엔티티와 일치하는지 확인 — 폐기된 키는 항상 불일치
    public boolean matches(String rawKey) {
        return !isRevoked() && keyHash.equals(hash(rawKey));
    }

    // 평문 키를 조회용 해시로 변환 — 인증 필터가 findByKeyHash 조회 키를 만들 때도 재사용
    public static String hash(String rawKey) {
        return sha256Hex(rawKey);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public void revoke() {
        this.revokedAt = Instant.now();
    }

    public void recordUsage(Instant now) {
        this.lastUsedAt = now;
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256Hex(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다", e);
        }
    }

    public record IssuedKey(TeamApiKey entity, String rawKey) {}
}
