// Project 엔티티 단위 테스트 — 생성 기본값·공개 상태 전이·primaryBranch blank 분기 회귀 방지
package com.codeprint.domain.project;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectTest {

    private final UUID userId = UUID.randomUUID();

    // 생성 헬퍼
    private Project newProject() {
        return Project.create(userId, "https://github.com/o/r", "repo", "desc");
    }

    @Test
    @DisplayName("create — 기본값으로 비공개·id·소유자·타임스탬프 설정")
    void create_setsDefaults() {
        Project p = newProject();

        assertThat(p.getId()).isNotNull();
        assertThat(p.getUserId()).isEqualTo(userId);
        assertThat(p.getGithubRepoUrl()).isEqualTo("https://github.com/o/r");
        assertThat(p.getName()).isEqualTo("repo");
        assertThat(p.isPublic()).isFalse();
        assertThat(p.getCreatedAt()).isNotNull();
        assertThat(p.getUpdatedAt()).isNotNull();
        assertThat(p.getPrimaryBranch()).isNull();
    }

    @Test
    @DisplayName("makePublic — 비공개에서 공개로 상태 전이")
    void makePublic_transitionsToPublic() {
        Project p = newProject();

        p.makePublic();

        assertThat(p.isPublic()).isTrue();
    }

    @Test
    @DisplayName("makePrivate — 공개에서 다시 비공개로 상태 전이")
    void makePrivate_transitionsToPrivate() {
        Project p = newProject();
        p.makePublic();

        p.makePrivate();

        assertThat(p.isPublic()).isFalse();
    }

    @Test
    @DisplayName("setPrimaryBranch — 정상 브랜치명은 그대로 저장")
    void setPrimaryBranch_storesValue() {
        Project p = newProject();

        p.setPrimaryBranch("develop");

        assertThat(p.getPrimaryBranch()).isEqualTo("develop");
    }

    @Test
    @DisplayName("setPrimaryBranch — 공백 문자열은 null로 정규화 (해제)")
    void setPrimaryBranch_blankBecomesNull() {
        Project p = newProject();
        p.setPrimaryBranch("main");

        p.setPrimaryBranch("   ");

        assertThat(p.getPrimaryBranch()).isNull();
    }

    @Test
    @DisplayName("setPrimaryBranch — null 입력은 null 유지")
    void setPrimaryBranch_nullStaysNull() {
        Project p = newProject();

        p.setPrimaryBranch(null);

        assertThat(p.getPrimaryBranch()).isNull();
    }

    @Test
    @DisplayName("getProjectId — id를 ProjectId VO로 래핑")
    void getProjectId_wrapsId() {
        Project p = newProject();

        assertThat(p.getProjectId()).isEqualTo(ProjectId.of(p.getId()));
    }
}
