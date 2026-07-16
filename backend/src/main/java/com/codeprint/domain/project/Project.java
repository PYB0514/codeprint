// 프로젝트 Aggregate Root 엔티티
package com.codeprint.domain.project;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Entity
@Table(name = "projects")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Project {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "github_repo_url", nullable = false, length = 500)
    private String githubRepoUrl;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "is_public", nullable = false)
    private boolean isPublic;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "primary_branch", length = 255)
    private String primaryBranch;

    // 1단계(architecture) 게이트 — 기본 켜짐. 레거시 코드베이스가 도입 즉시 마이그레이션을 강제당하지 않도록 끌 수 있음.
    @Column(name = "gate_architecture_enabled", nullable = false)
    private boolean gateArchitectureEnabled;

    // 2단계(experimental) 게이트 — 기본 꺼짐. 아직 교차 프로젝트 실사용 검증이 부족한 신규 룰까지 적용받고 싶은 팀만 켬.
    @Column(name = "gate_experimental_enabled", nullable = false)
    private boolean gateExperimentalEnabled;

    // PR 게이트 webhook 서명 시크릿 — 프로젝트별 발급, 연결 전엔 null
    @Column(name = "webhook_secret", length = 64)
    private String webhookSecret;

    // 사용자 ID와 GitHub URL로 새 프로젝트 인스턴스 생성
    public static Project create(UUID userId, String githubRepoUrl, String name, String description) {
        Project project = new Project();
        project.id = UUID.randomUUID();
        project.userId = userId;
        project.githubRepoUrl = githubRepoUrl;
        project.name = name;
        project.description = description;
        project.isPublic = false;
        project.createdAt = Instant.now();
        project.updatedAt = Instant.now();
        project.gateArchitectureEnabled = true;
        project.gateExperimentalEnabled = false;
        return project;
    }

    // 주요 브랜치 설정 (null 허용 — 해제 시 null)
    public void setPrimaryBranch(String branch) {
        this.primaryBranch = (branch != null && branch.isBlank()) ? null : branch;
        this.updatedAt = Instant.now();
    }

    // 프로젝트를 공개 상태로 전환
    public void makePublic() {
        this.isPublic = true;
        this.updatedAt = Instant.now();
    }

    // 프로젝트를 비공개 상태로 전환
    public void makePrivate() {
        this.isPublic = false;
        this.updatedAt = Instant.now();
    }

    // 레포 owner가 프로젝트 소유자의 GitHub 계정과 일치하는지 (내 레포 vs 외부 레포 분석 판정)
    public boolean isOwnRepo(String ownerUsername) {
        return com.codeprint.shared.GithubRepoOwner.matches(githubRepoUrl, ownerUsername);
    }

    // 1단계(architecture) 게이트 켬/끔 전환
    public void setGateArchitectureEnabled(boolean enabled) {
        this.gateArchitectureEnabled = enabled;
        this.updatedAt = Instant.now();
    }

    // 2단계(experimental) 게이트 켬/끔 전환
    public void setGateExperimentalEnabled(boolean enabled) {
        this.gateExperimentalEnabled = enabled;
        this.updatedAt = Instant.now();
    }

    // UUID를 ProjectId Value Object로 변환하여 반환
    public ProjectId getProjectId() {
        return ProjectId.of(id);
    }

    // PR 게이트 webhook 시크릿 신규 발급(이미 있어도 교체) — 32byte 난수를 hex로 인코딩
    public void generateWebhookSecret() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        this.webhookSecret = HexFormat.of().formatHex(bytes);
        this.updatedAt = Instant.now();
    }

    // PR 게이트 연결 해제 — 시크릿 제거로 webhook 서명 검증 불가 상태로 전환
    public void disconnectPrGate() {
        this.webhookSecret = null;
        this.updatedAt = Instant.now();
    }

    // PR 게이트 연결 여부 — 시크릿 발급 완료 상태
    public boolean isPrGateConnected() {
        return webhookSecret != null;
    }
}
