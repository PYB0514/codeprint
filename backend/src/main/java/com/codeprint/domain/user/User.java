// 사용자 Aggregate Root 엔티티
package com.codeprint.domain.user;

import com.codeprint.shared.jpa.AesEncryptionConverter;
import com.codeprint.shared.plan.UserPlan;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    // 활동 기록 쓰로틀 — 마지막 기록이 이 간격 이내면 갱신 생략 (인증 핫패스 write 억제)
    private static final Duration ACTIVITY_THROTTLE = Duration.ofMinutes(10);

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Convert(converter = AesEncryptionConverter.class)
    @Column(name = "github_access_token_encrypted", length = 500)
    private String githubAccessToken;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "graph_bg_url", length = 500)
    private String graphBgUrl;

    @Column(name = "last_active_at")
    private Instant lastActiveAt;

    // GitHub 사용자 정보로 새 User 인스턴스 생성
    public static User create(Long githubId, String email, String username) {
        User user = new User();
        user.id = UUID.randomUUID();
        user.githubId = githubId;
        user.email = email;
        user.username = username;
        user.plan = UserPlan.FREE;
        user.role = UserRole.USER;
        user.enabled = true;
        user.createdAt = Instant.now();
        user.updatedAt = Instant.now();
        return user;
    }

    // GitHub OAuth access token 갱신
    public void updateGithubAccessToken(String token) {
        this.githubAccessToken = token;
        this.updatedAt = Instant.now();
    }

    // 사용자 플랜을 DESKTOP으로 업그레이드
    public void upgradeToPro() {
        this.plan = UserPlan.DESKTOP;
        this.updatedAt = Instant.now();
    }

    // 사용자 플랜을 FREE로 다운그레이드
    public void downgradeToFree() {
        this.plan = UserPlan.FREE;
        this.updatedAt = Instant.now();
    }

    // 계정을 정지 상태로 변경
    public void disable() {
        this.enabled = false;
        this.updatedAt = Instant.now();
    }

    // 계정을 활성 상태로 복구
    public void enable() {
        this.enabled = true;
        this.updatedAt = Instant.now();
    }

    // 프로필 아바타 URL 업데이트
    public void updateAvatarUrl(String url) {
        this.avatarUrl = url;
        this.updatedAt = Instant.now();
    }

    // 그래프 배경 이미지 URL 업데이트
    public void updateGraphBgUrl(String url) {
        this.graphBgUrl = url;
        this.updatedAt = Instant.now();
    }

    // 활동 시각 기록 — 마지막 기록이 쓰로틀보다 오래됐을 때만 갱신, 갱신 여부 반환
    public boolean recordActivity(Instant now) {
        if (lastActiveAt != null && lastActiveAt.isAfter(now.minus(ACTIVITY_THROTTLE))) {
            return false;
        }
        this.lastActiveAt = now;
        return true;
    }

    // UUID를 UserId Value Object로 변환하여 반환
    public UserId getUserId() {
        return UserId.of(id);
    }
}
