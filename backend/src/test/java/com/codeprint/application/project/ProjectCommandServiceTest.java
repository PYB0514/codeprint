// ProjectCommandService 단위 테스트 — 플랜별 프로젝트 수 제한(경계)·소유권 검증·URL 검증 회귀 방지
package com.codeprint.application.project;

import com.codeprint.domain.project.Project;
import com.codeprint.domain.project.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectCommandServiceTest {

    @Mock private ProjectRepository projectRepository;

    private ProjectCommandService service;

    private static final String VALID_URL = "https://github.com/PYB0514/codeprint";

    @BeforeEach
    void setUp() {
        service = new ProjectCommandService(projectRepository);
    }

    // --- createProject: 프로젝트 수 제한 경계 ---

    @Test
    @DisplayName("createProject — FREE(max=3): 현재 2개면 생성 허용 (경계 바로 아래)")
    void createProject_underLimit_allowed() {
        UUID userId = UUID.randomUUID();
        when(projectRepository.countByUserId(userId)).thenReturn(2);
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        Project created = service.createProject(userId, VALID_URL, "codeprint", "desc", 3);

        assertThat(created.getUserId()).isEqualTo(userId);
        assertThat(created.getGithubRepoUrl()).isEqualTo(VALID_URL);
        assertThat(created.isPublic()).isFalse();
        verify(projectRepository).save(any(Project.class));
    }

    @Test
    @DisplayName("createProject — FREE(max=3): 현재 3개면 제한 초과로 거부 (경계값)")
    void createProject_atLimit_rejected() {
        UUID userId = UUID.randomUUID();
        when(projectRepository.countByUserId(userId)).thenReturn(3);

        assertThatThrownBy(() -> service.createProject(userId, VALID_URL, "n", "d", 3))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Project limit reached");
        verify(projectRepository, never()).save(any());
    }

    @Test
    @DisplayName("createProject — PRO(max=MAX_VALUE): 다수 보유해도 생성 허용")
    void createProject_proUnlimited_allowed() {
        UUID userId = UUID.randomUUID();
        when(projectRepository.countByUserId(userId)).thenReturn(1000);
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        Project created = service.createProject(userId, VALID_URL, "n", "d", Integer.MAX_VALUE);

        assertThat(created).isNotNull();
        verify(projectRepository).save(any(Project.class));
    }

    @Test
    @DisplayName("createProject — 잘못된 GitHub URL은 IllegalArgumentException, 제한 검사 이전에 차단")
    void createProject_invalidUrl_rejectedBeforeLimitCheck() {
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() -> service.createProject(userId, "https://gitlab.com/a/b", "n", "d", 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid GitHub repository URL");
        verify(projectRepository, never()).countByUserId(any());
        verify(projectRepository, never()).save(any());
    }

    @Test
    @DisplayName("createProject — http(비https)·다른 호스트·비URL은 모두 거부")
    void createProject_variousInvalidUrls_rejected() {
        UUID userId = UUID.randomUUID();
        for (String bad : new String[]{"http://github.com/a/b", "https://github.com/onlyone", "not-a-url", "https://github.com/a/b/c"}) {
            assertThatThrownBy(() -> service.createProject(userId, bad, "n", "d", 3))
                    .as("URL: %s", bad)
                    .isInstanceOf(IllegalArgumentException.class);
        }
        verify(projectRepository, never()).save(any());
    }

    // --- toggleVisibility: 소유권 + 공개/비공개 전환 ---

    @Test
    @DisplayName("toggleVisibility — 소유자가 makePublic 시 공개 상태로 전환·저장")
    void toggleVisibility_ownerMakePublic() {
        UUID ownerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Project project = Project.create(ownerId, VALID_URL, "n", "d");
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(projectRepository.save(project)).thenReturn(project);

        Project result = service.toggleVisibility(projectId, ownerId, true);

        assertThat(result.isPublic()).isTrue();
        verify(projectRepository).save(project);
    }

    @Test
    @DisplayName("toggleVisibility — 소유자가 makePrivate 시 비공개 상태로 전환")
    void toggleVisibility_ownerMakePrivate() {
        UUID ownerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Project project = Project.create(ownerId, VALID_URL, "n", "d");
        project.makePublic();
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(projectRepository.save(project)).thenReturn(project);

        Project result = service.toggleVisibility(projectId, ownerId, false);

        assertThat(result.isPublic()).isFalse();
    }

    @Test
    @DisplayName("toggleVisibility — 소유자가 아니면 IllegalStateException, 저장 안 함")
    void toggleVisibility_notOwner_rejected() {
        UUID ownerId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Project project = Project.create(ownerId, VALID_URL, "n", "d");
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> service.toggleVisibility(projectId, otherId, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Not authorized");
        verify(projectRepository, never()).save(any());
    }

    @Test
    @DisplayName("toggleVisibility — 존재하지 않는 프로젝트면 IllegalArgumentException")
    void toggleVisibility_notFound_rejected() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.findById(projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.toggleVisibility(projectId, UUID.randomUUID(), true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Project not found");
    }

    // --- setPrimaryBranch: 소유권 ---

    @Test
    @DisplayName("setPrimaryBranch — 소유자면 브랜치 설정·저장")
    void setPrimaryBranch_owner() {
        UUID ownerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Project project = Project.create(ownerId, VALID_URL, "n", "d");
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(projectRepository.save(project)).thenReturn(project);

        Project result = service.setPrimaryBranch(projectId, ownerId, "develop");

        assertThat(result.getPrimaryBranch()).isEqualTo("develop");
        verify(projectRepository).save(project);
    }

    @Test
    @DisplayName("setPrimaryBranch — 소유자가 아니면 IllegalStateException, 저장 안 함")
    void setPrimaryBranch_notOwner_rejected() {
        UUID ownerId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Project project = Project.create(ownerId, VALID_URL, "n", "d");
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> service.setPrimaryBranch(projectId, otherId, "develop"))
                .isInstanceOf(IllegalStateException.class);
        verify(projectRepository, never()).save(any());
    }

    // --- deleteProject: 소유권 ---

    @Test
    @DisplayName("deleteProject — 소유자면 삭제")
    void deleteProject_owner() {
        UUID ownerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Project project = Project.create(ownerId, VALID_URL, "n", "d");
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        service.deleteProject(projectId, ownerId);

        verify(projectRepository).deleteById(projectId);
    }

    @Test
    @DisplayName("deleteProject — 소유자가 아니면 IllegalStateException, 삭제 안 함")
    void deleteProject_notOwner_rejected() {
        UUID ownerId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Project project = Project.create(ownerId, VALID_URL, "n", "d");
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> service.deleteProject(projectId, otherId))
                .isInstanceOf(IllegalStateException.class);
        verify(projectRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("deleteProject — 존재하지 않는 프로젝트면 IllegalArgumentException")
    void deleteProject_notFound_rejected() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.findById(projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteProject(projectId, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
        verify(projectRepository, never()).deleteById(any());
    }
}
