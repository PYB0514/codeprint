// ProjectQueryService 단위 테스트 — getProject 소유자 인가·getPublicProject 공개 검증 회귀 방지
package com.codeprint.application.project;

import com.codeprint.domain.project.Project;
import com.codeprint.domain.project.ProjectRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectQueryServiceTest {

    @Mock
    private ProjectRepository repository;

    @InjectMocks
    private ProjectQueryService service;

    private final UUID ownerId = UUID.randomUUID();
    private final UUID otherId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();

    @Test
    @DisplayName("사용자 프로젝트 목록 — 레포지토리 위임")
    void getProjectsByUser_delegates() {
        Project p = project(ownerId);
        when(repository.findByUserId(ownerId)).thenReturn(List.of(p));

        assertThat(service.getProjectsByUser(ownerId)).containsExactly(p);
    }

    @Test
    @DisplayName("repo URL 조회 — 레포지토리 위임")
    void findByRepoUrl_delegates() {
        Project p = project(ownerId);
        when(repository.findByRepoUrl("https://github.com/x/y")).thenReturn(List.of(p));

        assertThat(service.findByRepoUrl("https://github.com/x/y")).containsExactly(p);
    }

    @Test
    @DisplayName("getProject — 소유자면 반환")
    void getProject_owner_returns() {
        Project p = project(ownerId);
        when(repository.findById(projectId)).thenReturn(Optional.of(p));

        assertThat(service.getProject(projectId, ownerId)).isSameAs(p);
    }

    @Test
    @DisplayName("getProject — 없으면 IllegalArgumentException")
    void getProject_notFound_throws() {
        when(repository.findById(projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getProject(projectId, ownerId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("getProject — 소유자 아니면 IllegalStateException")
    void getProject_notOwner_throws() {
        Project p = project(ownerId);
        when(repository.findById(projectId)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.getProject(projectId, otherId))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("getPublicProject — 공개면 반환")
    void getPublicProject_public_returns() {
        Project p = project(ownerId);
        p.makePublic();
        when(repository.findById(projectId)).thenReturn(Optional.of(p));

        assertThat(service.getPublicProject(projectId)).isSameAs(p);
    }

    @Test
    @DisplayName("getPublicProject — 없으면 IllegalArgumentException")
    void getPublicProject_notFound_throws() {
        when(repository.findById(projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPublicProject(projectId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("getPublicProject — 비공개면 IllegalStateException")
    void getPublicProject_notPublic_throws() {
        Project p = project(ownerId); // 기본 비공개
        when(repository.findById(projectId)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.getPublicProject(projectId))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("searchPublic — 프로젝트명·레포 URL로 필터")
    void searchPublic_filtersByNameOrRepoUrl() {
        Project match = Project.create(ownerId, "https://github.com/x/codeprint", "codeprint", null);
        Project noMatch = Project.create(ownerId, "https://github.com/x/other", "other", null);
        when(repository.findAllPublic(any())).thenReturn(List.of(match, noMatch));

        assertThat(service.searchPublic("codeprint")).containsExactly(match);
    }

    @Test
    @DisplayName("searchPublic — query 없으면 전체 반환")
    void searchPublic_noQuery_returnsAll() {
        Project p1 = project(ownerId);
        Project p2 = project(otherId);
        when(repository.findAllPublic(any())).thenReturn(List.of(p1, p2));

        assertThat(service.searchPublic(null)).containsExactly(p1, p2);
    }

    private Project project(UUID userId) {
        return Project.create(userId, "https://github.com/x/y", "y", "desc");
    }
}
