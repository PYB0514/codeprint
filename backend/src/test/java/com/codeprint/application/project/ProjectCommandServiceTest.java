// ProjectCommandService 단위 테스트 — 소유권 검증·URL 검증 회귀 방지
package com.codeprint.application.project;

import com.codeprint.domain.project.Project;
import com.codeprint.domain.project.ProjectRepository;
import com.codeprint.domain.project.port.GraphWarningsCachePort;
import com.codeprint.shared.gate.GatePolicy;
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
    @Mock private GraphWarningsCachePort graphWarningsCachePort;

    private ProjectCommandService service;

    private static final String VALID_URL = "https://github.com/PYB0514/codeprint";

    @BeforeEach
    void setUp() {
        service = new ProjectCommandService(projectRepository, graphWarningsCachePort);
    }

    // --- createProject: URL 검증 + 생성 ---

    @Test
    @DisplayName("createProject — 유효한 URL이면 비공개 프로젝트 생성")
    void createProject_valid_created() {
        UUID userId = UUID.randomUUID();
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        Project created = service.createProject(userId, VALID_URL, "codeprint", "desc");

        assertThat(created.getUserId()).isEqualTo(userId);
        assertThat(created.getGithubRepoUrl()).isEqualTo(VALID_URL);
        assertThat(created.isPublic()).isFalse();
        verify(projectRepository).save(any(Project.class));
    }

    @Test
    @DisplayName("createProject — 잘못된 GitHub URL은 IllegalArgumentException")
    void createProject_invalidUrl_rejected() {
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() -> service.createProject(userId, "https://gitlab.com/a/b", "n", "d"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid GitHub repository URL");
        verify(projectRepository, never()).save(any());
    }

    @Test
    @DisplayName("createProject — http(비https)·다른 호스트·비URL은 모두 거부")
    void createProject_variousInvalidUrls_rejected() {
        UUID userId = UUID.randomUUID();
        for (String bad : new String[]{"http://github.com/a/b", "https://github.com/onlyone", "not-a-url", "https://github.com/a/b/c"}) {
            assertThatThrownBy(() -> service.createProject(userId, bad, "n", "d"))
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

    // --- setGatePolicy: 소유권 + 경고 캐시 무효화(P2-C 회귀 방지) ---

    @Test
    @DisplayName("setGatePolicy — 소유자면 정책 저장 후 graphWarnings 캐시 전체 무효화")
    void setGatePolicy_owner_evictsWarningsCache() {
        UUID ownerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Project project = Project.create(ownerId, VALID_URL, "n", "d");
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(projectRepository.save(project)).thenReturn(project);

        Project result = service.setGatePolicy(projectId, ownerId, GatePolicy.DDD);

        assertThat(result.getGatePolicy()).isEqualTo(GatePolicy.DDD);
        verify(graphWarningsCachePort).evictAll();
    }

    @Test
    @DisplayName("setGatePolicy — 소유자가 아니면 IllegalStateException, 캐시 무효화 안 함")
    void setGatePolicy_notOwner_rejected() {
        UUID ownerId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Project project = Project.create(ownerId, VALID_URL, "n", "d");
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> service.setGatePolicy(projectId, otherId, GatePolicy.LAYERED))
                .isInstanceOf(IllegalStateException.class);
        verify(projectRepository, never()).save(any());
        verifyNoInteractions(graphWarningsCachePort);
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
