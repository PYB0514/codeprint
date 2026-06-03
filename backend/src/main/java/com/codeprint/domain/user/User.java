// 사용자 Aggregate Root 엔티티
package com.codeprint.domain.user;

import com.codeprint.infrastructure.security.AesEncryptionConverter;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "github_id", unique = true, nullable = false)
    private Long githubId;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false, length = 100)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserPlan plan;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Convert(converter = AesEncryptionConverter.class)
    @Column(name = "github_access_token", length = 500)
    private String githubAccessToken;

    // GitHub 사용자 정보로 새 User 인스턴스 생성
    public static User create(Long githubId, String email, String username) {
        User user = new User();
        user.id = UUID.randomUUID();
        user.githubId = githubId;
        user.email = email;
        user.username = username;
        user.plan = UserPlan.FREE;
        user.createdAt = Instant.now();
        user.updatedAt = Instant.now();
        return user;
    }

    // GitHub OAuth access token 갱신
    public void updateGithubAccessToken(String token) {
        this.githubAccessToken = token;
        this.updatedAt = Instant.now();
    }

    // 사용자 플랜을 PRO로 업그레이드
    public void upgradeToPro() {
        this.plan = UserPlan.PRO;
        this.updatedAt = Instant.now();
    }

    // 사용자 플랜을 FREE로 다운그레이드
    public void downgradeToFree() {
        this.plan = UserPlan.FREE;
        this.updatedAt = Instant.now();
    }

    // UUID를 UserId Value Object로 변환하여 반환
    public UserId getUserId() {
        return UserId.of(id);
    }
}
