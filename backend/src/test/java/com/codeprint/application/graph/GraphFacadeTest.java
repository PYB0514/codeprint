// GraphFacade 단위 테스트 — verifyGraphReadAccess 공개/소유자 분기 회귀 방지
package com.codeprint.application.graph;

import com.codeprint.domain.graph.Graph;
import com.codeprint.domain.graph.port.AnalysisReadPort;
import com.codeprint.domain.graph.port.ProjectAccessPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GraphFacadeTest {

    @Mock
    private GraphQueryService graphQueryService;
    @Mock
    private ProjectAccessPort projectAccessPort;
    @Mock
    private AnalysisReadPort analysisReadPort;

    @InjectMocks
    private GraphFacade facade;

    private final UUID graphId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final UUID otherId = UUID.randomUUID();

    private Graph graph(UUID pid) {
        return Graph.create(pid, UUID.randomUUID());
    }

    @Test
    @DisplayName("verifyGraphReadAccess — 공개 프로젝트면 비로그인(userId=null)도 통과")
    void publicProject_anonymous_passes() {
        when(graphQueryService.findById(graphId)).thenReturn(Optional.of(graph(projectId)));
        doNothing().when(projectAccessPort).verifyPublic(projectId);

        facade.verifyGraphReadAccess(graphId, null);
        // 예외 없이 통과하면 성공
    }

    @Test
    @DisplayName("verifyGraphReadAccess — 공개 프로젝트면 로그인 사용자(비소유자)도 통과")
    void publicProject_otherUser_passes() {
        when(graphQueryService.findById(graphId)).thenReturn(Optional.of(graph(projectId)));
        doNothing().when(projectAccessPort).verifyPublic(projectId);

        facade.verifyGraphReadAccess(graphId, otherId);
    }

    @Test
    @DisplayName("verifyGraphReadAccess — 비공개 프로젝트는 소유자만 통과")
    void privateProject_owner_passes() {
        when(graphQueryService.findById(graphId)).thenReturn(Optional.of(graph(projectId)));
        doThrow(new IllegalStateException("Project is not public")).when(projectAccessPort).verifyPublic(projectId);
        doNothing().when(projectAccessPort).verifyOwnership(projectId, ownerId);

        facade.verifyGraphReadAccess(graphId, ownerId);
    }

    @Test
    @DisplayName("verifyGraphReadAccess — 비공개 프로젝트 + 비로그인은 차단")
    void privateProject_anonymous_throws() {
        when(graphQueryService.findById(graphId)).thenReturn(Optional.of(graph(projectId)));
        doThrow(new IllegalStateException("Project is not public")).when(projectAccessPort).verifyPublic(projectId);

        assertThatThrownBy(() -> facade.verifyGraphReadAccess(graphId, null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("verifyGraphReadAccess — 비공개 프로젝트 + 비소유자는 차단")
    void privateProject_nonOwner_throws() {
        when(graphQueryService.findById(graphId)).thenReturn(Optional.of(graph(projectId)));
        doThrow(new IllegalStateException("Project is not public")).when(projectAccessPort).verifyPublic(projectId);
        doThrow(new IllegalStateException("Not authorized to access this project"))
                .when(projectAccessPort).verifyOwnership(projectId, otherId);

        assertThatThrownBy(() -> facade.verifyGraphReadAccess(graphId, otherId))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("verifyGraphReadAccess — 그래프 없으면 IllegalArgumentException")
    void graphNotFound_throws() {
        when(graphQueryService.findById(graphId)).thenReturn(Optional.empty());

        assertThat(assertThatThrownBy(() -> facade.verifyGraphReadAccess(graphId, ownerId))
                .isInstanceOf(IllegalArgumentException.class)).isNotNull();
    }
}
